package org.wordpress.android.ui.uploads

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.PageStore
import org.wordpress.android.fluxc.store.PostStore
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.util.NetworkUtilsWrapper
import javax.inject.Inject
import javax.inject.Named

/**
 * Provides a way to find and upload all local drafts.
 */
class LocalDraftUploadStarter @Inject constructor(
    /**
     * The Application context
     */
    private val context: Context,
    private val postStore: PostStore,
    private val pageStore: PageStore,
    /**
     * The Coroutine dispatcher used for querying in FluxC.
     */
    @Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher,
    private val networkUtilsWrapper: NetworkUtilsWrapper
) {
    fun uploadLocalDrafts(scope: CoroutineScope, site: SiteModel) = scope.launch(bgDispatcher) {
        if (!networkUtilsWrapper.isNetworkAvailable()) {
            return@launch
        }

        val posts = async { postStore.getLocalDraftPosts(site) }
        val pages = async { pageStore.getLocalDraftPages(site) }

        val postsAndPages = posts.await() + pages.await()

        postsAndPages.filterNot { UploadService.isPostUploadingOrQueued(it) }
                .forEach { localDraft ->
                    val intent = UploadService.getUploadPostServiceIntent(context, localDraft, false, false, true)
                    context.startService(intent)
                }
    }
}
