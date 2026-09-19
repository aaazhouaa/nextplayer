package dev.anilbeesetti.nextplayer.core.common.service.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dev.anilbeesetti.nextplayer.core.common.extensions.getPath
import dev.anilbeesetti.nextplayer.core.common.service.SuspendActivityResultLauncher
import dev.anilbeesetti.nextplayer.core.common.service.registerForSuspendActivityResult
import org.koin.core.annotation.Single

@Single
class LocalSystemService(
    private val context: Context,
) : SystemService {
    private var pickDocumentTreeLauncher: SuspendActivityResultLauncher<Uri?, Uri?>? = null

    override fun initialize(activity: ComponentActivity) {
        pickDocumentTreeLauncher = activity.registerForSuspendActivityResult(ActivityResultContracts.OpenDocumentTree())
    }

    override suspend fun pickFolder(): Uri? {
        return pickDocumentTreeLauncher?.launch(null)?.also { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
    }

    override suspend fun pickFolderPath(): String? {
        return pickFolder()?.let { uri -> uri.toTreePath() }
    }

    /** Resolves an OpenDocumentTree tree uri to a file-system path, or null if unsupported. */
    private fun Uri.toTreePath(): String? {
        // Tree URIs from ExternalStorageProvider use the `primary:<relative-path>` document id.
        if (DocumentsContract.isTreeUri(this)) {
            val docId = DocumentsContract.getTreeDocumentId(this)
            val split = docId.split(":".toRegex(), limit = 2)
            if (split.size == 2 && split[0].equals("primary", ignoreCase = true)) {
                val relative = split[1].trim('/')
                return if (relative.isEmpty()) {
                    Environment.getExternalStorageDirectory().path
                } else {
                    Environment.getExternalStorageDirectory().path + "/" + relative
                }
            }
            return null
        }
        return context.getPath(this)
    }

    override fun getString(stringResId: Int): String = context.getString(stringResId)

    override fun getQuantityString(
        pluralsResId: Int,
        quantity: Int,
        vararg formatArgs: Any,
    ): String = context.resources.getQuantityString(pluralsResId, quantity, *formatArgs)

    override fun showToast(text: String, duration: Int) {
        Toast.makeText(context, text, duration).show()
    }
}
