# JVM installation and API guide

eXcor extracts YouTube/YouTube Music metadata and transport details. Your app
owns playback and media storage. Start with signed-out metadata; direct media
availability varies by video and network. See the [supported feature matrix](FEATURE_MATRIX.md)
and [playback evidence](PLAYBACK_EVIDENCE_MATRIX.md) before choosing a transport.

## Install from this checkout

There is no public Maven repository for eXcor yet. The current source version is
`0.2.0`; adding the coordinate to Maven Central alone will not install it.
Use a local build from the revision you intend to integrate.

From the repository root, with JDK 21 available:

```sh
./gradlew :publishJvmPublicationToMavenLocal :publishKotlinMultiplatformPublicationToMavenLocal
./gradlew -p modules/samples/jvm-quickstart build
./gradlew -p modules/samples/jvm-quickstart run --args="--help"
```

On Windows, use `gradlew.bat`. The first command installs the JVM artifact and
root multiplatform metadata in your local Maven repository. It does not publish
to a remote service. These tasks are enough for this JVM consumer; the aggregate
`publishToMavenLocal` also builds other targets and needs their toolchains.
The library targets Java 17 bytecode; its source build uses JDK 21.

The sample's `build` and `--help` commands make no YouTube requests. Running it
without arguments makes live metadata requests and can fail if access is denied:

```sh
./gradlew -p modules/samples/jvm-quickstart run
```

For a separate JVM application, use this `build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    application
}

repositories {
    mavenLocal()
    mavenCentral()
    google()
}

dependencies {
    implementation("com.excor:eXcor:0.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

java { targetCompatibility = JavaVersion.VERSION_17 }
application { mainClass.set("MainKt") }
```

Declare coroutines directly because the application uses them. The repository's
[standalone consumer](../modules/samples/jvm-quickstart/build.gradle.kts) follows
this dependency setup. For an Android or iOS integration, use the corresponding
platform source/build entry points in [architecture](ARCHITECTURE.md); this guide
only verifies the JVM path. The [native Swift package](../modules/platforms/swift/Package.swift)
is a separate deliverable.

## Read metadata and one continuation page

Save this as `src/main/kotlin/Main.kt`:

```kotlin
import com.excor.Excor
import com.excor.ExcorRequestException
import com.excor.excorDefaultHost
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val host = excorDefaultHost(region = "US", language = "en")
    val excor = Excor(host)
    try {
        val video = excor.videoInfo("dQw4w9WgXcQ")
        println(video?.title ?: "Video metadata unavailable")

        val query = "daft punk"
        val first = excor.search(query)
        println("First page: ${first?.videos?.size ?: 0} videos")
        val second = first?.continuation?.let { token ->
            excor.search(query, continuation = token)
        }
        println("Second page: ${second?.videos?.size ?: 0} videos")
    } catch (error: ExcorRequestException) {
        println("Request failed: ${error.failure}")
    } finally {
        excor.close()
        host.httpClient.connectionPool.evictAll()
        host.httpClient.dispatcher.executorService.shutdown()
    }
}
```

This example owns both the library and the default host client. `Excor.close()`
cancels background warm-up and closes the library's transport; it leaves the host client open. When injecting a
shared `OkHttpClient`, let its owner decide when to shut it down. Reuse an Excor
instance for related operations and close it when that owner finishes.
Repeated initialization shares an active warm-up. `onSessionChanged()` cancels
the preceding warm-up and schedules fresh work when the host config store is enabled. These
background callbacks follow cooperative coroutine cancellation. If the session callback
returns after cancellation, warm-up stops before invoking the token provider.

Methods suspend: call them from a coroutine. `runBlocking` is appropriate for this
command-line example; UI applications should use their existing coroutine scope.
Cancellation propagates. A `null` result means no recognized result was returned;
it does not prove that the content is deleted. See [error handling](ERROR_HANDLING.md)
for transient request failures, retries and partial results.

Keep the query and selected filter parameters unchanged when sending back a
continuation. Tokens are opaque. Stop when the next continuation is absent, and
bound pagination in your application; the example fetches at most two pages.

## Choose the operation

| Need | Public API | Host responsibility |
|---|---|---|
| Video metadata | `videoInfo(videoId)` | Handle null, playability and request failures |
| Search/filter/more | `search(query, continuation, filterParams)` | Preserve query/filter context and bound page count |
| Playlist or channel tab | `playlist(playlistId, continuation)`, `channel(channelId, continuation, tab)` | Preserve IDs/tab while paging |
| YouTube Music | `musicSearch`, `musicArtist`, `musicPlaylist`, `musicRadio` | Use each result's continuation contract |
| Direct audio | `extract(videoId, maxKbps)` | Supply returned headers to a compatible player |
| Video plus audio | `extractVideo(videoId, maxHeight, codecs, maxAudioKbps)` | Play/synchronize the returned tracks |
| One client's whole-file formats | `formats(videoId)` | Select formats and manage playback/downloads |
| Provider catalogs | `music`, `musicCatalog` | Follow [provider API scope](MUSIC_API.md) and token requirements |

Extraction can return null or throw a sanitized playability refusal; metadata
request exceptions alone do not cover every extraction outcome. Media URLs are
short-lived transport details, not permanent content identifiers. Do not log or
persist signed URLs or session values. Returned request headers belong with their
stream; a readable range does not establish full decode or player seeking.
HLS handoff is experimental; production DASH and SABR/UMP playback remain
unsupported. Check the matrices for dated platform and live-test limits.
