package com.matepazy.spectre.support

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

data class GitHubAsset(
    val name: String,
    val browser_download_url: String
)

data class GitHubRelease(
    val tag_name: String,
    val prerelease: Boolean,
    val name: String?,
    val body: String?,
    val assets: List<GitHubAsset>
)

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpdateAvailable(
        val version: String,
        val notes: String,
        val downloadUrl: String
    ) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Completed(val apkFile: File) : UpdateState
    data class Error(val message: String) : UpdateState
}

object SemanticVersion {
    fun parse(version: String): Triple<Int, Int, Int> {
        val clean = version.trimStart('v', 'V').substringBefore('-').substringBefore('+')
        val parts = clean.split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    fun isNewer(remote: String, local: String): Boolean {
        val (rMajor, rMinor, rPatch) = parse(remote)
        val (lMajor, lMinor, lPatch) = parse(local)
        
        if (rMajor != lMajor) return rMajor > lMajor
        if (rMinor != lMinor) return rMinor > lMinor
        return rPatch > lPatch
    }
}

object VersionUpdater {
    private val client = OkHttpClient()
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    
    private val releasesAdapter = moshi.adapter<List<GitHubRelease>>(
        Types.newParameterizedType(List::class.java, GitHubRelease::class.java)
    )

    fun checkForUpdates(
        currentVersion: String,
        channel: String, // "release" or "pre-release"
        onResult: (UpdateState) -> Unit
    ) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/matepazy/spectre-app/releases")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onResult(UpdateState.Error("Network error: ${e.localizedMessage}"))
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    onResult(UpdateState.Error("GitHub API error: ${response.code}"))
                    return
                }

                try {
                    val bodyString = response.body?.string() ?: ""
                    val releases = releasesAdapter.fromJson(bodyString) ?: emptyList()
                    
                    // Filter according to update channel
                    val targetRelease = releases.firstOrNull { release ->
                        if (channel == "release") {
                            !release.prerelease
                        } else {
                            true
                        }
                    }

                    if (targetRelease == null) {
                        onResult(UpdateState.Idle)
                        return
                    }

                    val remoteTag = targetRelease.tag_name
                    val apkAsset = targetRelease.assets.firstOrNull { it.name.endsWith(".apk") }

                    if (SemanticVersion.isNewer(remoteTag, currentVersion)) {
                        if (apkAsset != null) {
                            onResult(
                                UpdateState.UpdateAvailable(
                                    version = remoteTag,
                                    notes = targetRelease.body ?: "No release notes provided.",
                                    downloadUrl = apkAsset.browser_download_url
                                )
                            )
                        } else {
                            onResult(UpdateState.Error("Update found but no APK asset is available in the release."))
                        }
                    } else {
                        onResult(UpdateState.Idle)
                    }
                } catch (e: Exception) {
                    onResult(UpdateState.Error("Failed to parse update: ${e.localizedMessage}"))
                }
            }
        })
    }

    fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit,
        onCompleted: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val request = Request.Builder()
            .url(downloadUrl)
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                onError("Download failed: ${e.localizedMessage}")
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                if (!response.isSuccessful) {
                    onError("Server error: ${response.code}")
                    return
                }

                val body = response.body
                if (body == null) {
                    onError("Response body is empty")
                    return
                }

                try {
                    val apkFile = File(context.cacheDir, "spectre_update.apk")
                    if (apkFile.exists()) {
                        apkFile.delete()
                    }

                    val totalBytes = body.contentLength()
                    var bytesRead: Long = 0
                    val buffer = ByteArray(8192)

                    body.byteStream().use { input ->
                        FileOutputStream(apkFile).use { output ->
                            var read = input.read(buffer)
                            while (read != -1) {
                                output.write(buffer, 0, read)
                                bytesRead += read
                                if (totalBytes > 0) {
                                    onProgress(bytesRead.toFloat() / totalBytes)
                                }
                                read = input.read(buffer)
                            }
                        }
                    }

                    onCompleted(apkFile)
                } catch (e: Exception) {
                    onError("Failed to save update file: ${e.localizedMessage}")
                }
            }
        })
    }
}

object ApkInstaller {
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
