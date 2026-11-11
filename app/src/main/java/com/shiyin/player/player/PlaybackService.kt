package com.shiyin.player.player

import android.net.Uri
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.shiyin.player.bili.BiliApi
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * 播放前台服务 —— Media3 MediaSession 托管:
 * 通知栏/锁屏控制、音频焦点、耳机拔出暂停均为框架内置。
 *
 * 音源不走本地代理:MediaItem 用占位 URI shiyin://track?bvid=&cid=&q=,
 * ResolvingDataSource 在打开时才调 playurl 解析直链,并给 CDN 附加 Referer。
 * 403 时标记缓存失效重试;连续失败自动跳曲,≥3 次暂停(网页版同款语义)。
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer

    /** 403 后需要强制重新解析的 key(bvid|cid|q) */
    private val staleKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var resolveRetries = 0
    private var failStreak = 0

    private fun staleKeyOf(uri: Uri): String? {
        val bvid = uri.getQueryParameter("bvid") ?: return null
        val cid = uri.getQueryParameter("cid") ?: return null
        val q = uri.getQueryParameter("q") ?: return null
        return "$bvid|$cid|$q"
    }

    private val resolver = object : ResolvingDataSource.Resolver {
        override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
            val uri = dataSpec.uri
            if (uri.scheme != "shiyin") return dataSpec
            val bvid = uri.getQueryParameter("bvid") ?: ""
            val cid = uri.getQueryParameter("cid")?.toLongOrNull() ?: 0L
            val q = uri.getQueryParameter("q") ?: "high"
            val stale = staleKeyOf(uri)?.let { staleKeys.remove(it) } ?: false
            val src = runBlocking {
                BiliApi.resolveAudio(bvid, cid, q, force = stale)
            }
            return dataSpec.buildUpon()
                .setUri(Uri.parse(src.url))
                .setHttpRequestHeaders(
                    mapOf(
                        "Referer" to "https://www.bilibili.com/video/$bvid",
                        "User-Agent" to BiliApi.UA,
                        "Accept" to "*/*",
                    )
                )
                .build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val httpFactory = OkHttpDataSource.Factory(BiliApi.http)
        val dataSourceFactory = DataSource.Factory {
            ResolvingDataSource(httpFactory.createDataSource(), resolver)
        }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(errorListener)
        session = MediaSession.Builder(this, player).build()
    }

    private val errorListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                failStreak = 0
                resolveRetries = 0
            }
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            resolveRetries = 0
            failStreak = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            val uri = player.currentMediaItem?.localConfiguration?.uri
            val canRetry = uri?.scheme == "shiyin" &&
                (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
            if (canRetry && resolveRetries < 2) {
                // 直链过期或瞬时网络错误:保留进度重新解析重试
                resolveRetries++
                staleKeyOf(uri)?.let { staleKeys.add(it) }
                val idx = player.currentMediaItemIndex
                val pos = player.currentPosition
                player.seekTo(idx, pos)
                player.prepare()
                player.play()
                return
            }
            failStreak++
            resolveRetries = 0
            if (failStreak >= 3) {
                failStreak = 0
                player.pause()
                Toast.makeText(this@PlaybackService, "连续播放失败,已暂停", Toast.LENGTH_SHORT).show()
            } else {
                // 单曲失败自动跳下一首
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                } else {
                    player.pause()
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }
}
