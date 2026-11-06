package app.samehadaku.dl.data.host

import app.samehadaku.dl.domain.model.Host
import app.samehadaku.dl.domain.model.HostResolveResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostResolverRegistry @Inject constructor(
    private val pixeldrainResolver: PixeldrainResolver,
    private val mediafireResolver: MediafireResolver,
) {

    suspend fun resolve(host: Host, url: String): HostResolveResult = when (host) {
        Host.PIXELDRAIN -> pixeldrainResolver.resolve(url)
        Host.MEDIAFIRE -> mediafireResolver.resolve(url)
    }
}
