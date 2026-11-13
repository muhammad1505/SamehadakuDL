package app.samehadaku.dl.data.host

import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.HostResolveResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostResolverRegistry @Inject constructor(
    private val pixeldrainResolver: PixeldrainResolver,
    private val mediafireResolver: MediafireResolver,
    private val gofileResolver: GofileResolver,
    private val acefileResolver: AcefileResolver,
    private val filedonResolver: FiledonResolver,
) {

    suspend fun resolve(host: Host, url: String): HostResolveResult = when (host) {
        Host.PIXELDRAIN -> pixeldrainResolver.resolve(url)
        Host.MEDIAFIRE -> mediafireResolver.resolve(url)
        Host.GOFILE -> gofileResolver.resolve(url)
        Host.ACEFILE -> acefileResolver.resolve(url)
        Host.FILEDON -> filedonResolver.resolve(url)
    }
}
