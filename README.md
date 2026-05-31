# MeetMusic

<p align="center">
  <strong>A Material-style Android music player powered by AndroidX Media3.</strong>
</p>

<p align="center">
  <a href="https://kotlinlang.org"><img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-1.9.0-7F52FF?logo=kotlin&logoColor=white"></a>
  <a href="https://developer.android.com"><img alt="Android" src="https://img.shields.io/badge/Android-SDK%2034-3DDC84?logo=android&logoColor=white"></a>
  <a href="https://developer.android.com/media/media3"><img alt="Media3" src="https://img.shields.io/badge/Media3-ExoPlayer-4285F4"></a>
  <img alt="Min SDK" src="https://img.shields.io/badge/minSdk-21-blue">
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue"></a>
</p>

<p align="center">
  <a href="#demo">Demo</a> ·
  <a href="#features">Features</a> ·
  <a href="#architecture">Architecture</a> ·
  <a href="#quick-start">Quick Start</a> ·
  <a href="#roadmap">Roadmap</a>
</p>

MeetMusic 是一个基于 Kotlin 与 AndroidX Media3 的 Android 音乐播放器示例项目。它把在线音乐流、MediaLibrarySession、前台播放服务、播放页频谱可视化、Material 风格设置页和模块化工程结构放在一起，适合作为学习 Media3 播放链路、音乐 App 架构拆分和自定义音频处理的参考。

## Demo

<table>
  <thead>
    <tr>
      <th>Android demo</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center">
        <a href="docs/assets/meetmusic-preview.mp4">
          <img src="docs/assets/meetmusic-preview.gif" alt="MeetMusic preview" width="320">
        </a>
      </td>
    </tr>
    <tr>
      <td><a href="docs/assets/meetmusic-preview.mp4">MP4</a></td>
    </tr>
  </tbody>
</table>

## Features

- Two-way feed: 首页使用自定义双向 Spanned Grid，支持横向、纵向边缘加载和空白预取。
- Online music source: 通过 Jamendo API 拉取音乐、封面、艺术家和播放地址。
- Media3 playback core: 基于 `MediaLibraryService`、`MediaLibrarySession`、`MediaBrowser` 和 `MediaController` 组织播放链路。
- Background playback: 支持前台播放服务、媒体通知、播放队列、上一首/下一首和随机播放命令。
- Now Playing page: 播放页包含封面、曲名、艺术家、进度条、播放控制和 FFT 频谱视图。
- Audio visualization: 自定义 `FFTAudioProcessor` 从 PCM 音频中提取频域数据，再通过 AIDL 回传给 UI。
- Personalization: 设置页支持语言、深色模式、Material 动态色/主题色、首页跨数、振动和缓存清理。
- Modular Android project: UI、播放服务、数据源、设置页、网络层和通用工具分别拆分为独立模块。

## Architecture

```text
MeetMusic
├── app                 # App shell, home feed, now-playing page, app PlaybackService
├── core-player-service # Media3 session service, ExoPlayer setup, FFT audio processor
├── biz-data            # Jamendo API service, domain models, constants
├── biz-settings        # Settings screens, theme/language preferences, cache controls
├── lib-base            # Base Activity/Fragment, utils, storage helpers, UI helpers
├── lib-net             # Retrofit, OkHttp, RxJava network foundation
└── lib-material        # Shared icons and image resources
```

### Playback Flow

```text
FeedsFragment
  -> MediaBrowser.getChildren("root")
  -> DemoMediaLibrarySessionCallback
  -> TracksRepository
  -> JamendoService
  -> MediaItem list
  -> MediaController / ExoPlayer
  -> NowPlayingActivity
```

### Audio Visualization Flow

```text
ExoPlayer AudioSink
  -> FFTAudioProcessor
  -> ExtraService AIDL callbacks
  -> NowPlayingActivity
  -> FFTBandView
```

## Tech Stack

| Area | Stack |
| --- | --- |
| Language | Kotlin, Java |
| UI | Android Views, ViewBinding, Material Components |
| Playback | AndroidX Media3, ExoPlayer, MediaSession |
| Network | Retrofit, OkHttp, RxJava/RxAndroid |
| Images | Glide |
| Audio | Media3 `AudioProcessor`, paramsen/noise FFT |
| Build | Gradle 8.0, Android Gradle Plugin 8.1.2 |

## Quick Start

### Requirements

- Android Studio with Android Gradle Plugin 8.x support
- JDK 17
- Android SDK 34
- Android device or emulator running Android 5.0+

### Build

```bash
git clone <your-repo-url>
cd MeetMusic
./gradlew :app:assembleDebug
```

### Install

```bash
./gradlew :app:installDebug
```

也可以直接用 Android Studio 打开项目，选择 `app` 运行配置后安装到设备。

## Configuration

项目当前使用 Jamendo 作为在线音乐数据源，基础地址在 `DemoPlaybackService` 中初始化，客户端参数在 `biz-data` 模块的 `ConfigC` 中维护。对外发布前建议把 API 配置迁移到本地配置、构建变量或服务端代理，避免把生产密钥直接写入源码。

## Permissions

| Permission | Why |
| --- | --- |
| `INTERNET` | 拉取音乐列表、音频流和封面 |
| `FOREGROUND_SERVICE` | 后台播放时维持播放服务 |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Android 14+ 媒体播放前台服务类型 |
| `RECORD_AUDIO` | 音频可视化/频谱相关能力 |

## Development Notes

- 首页分页由 `FeedsViewModel` 维护缓存队列和预取请求，避免滚动到边缘时出现明显空白。
- `DemoMediaLibrarySessionCallback` 负责把 Jamendo 数据转成 Media3 `MediaItem`，并处理媒体控制器的播放列表解析。
- `FFTAudioProcessor` 插入 ExoPlayer 音频渲染链路，保留原音频输出，同时生成 FFT 数据供 UI 展示。
- 设置页通过 `SPUtils` 保存偏好，并使用 `RxBus` 通知主题等全局变化。

## Roadmap

- Add release signing and CI build workflow.
- Add unit tests for feed prefetching and media item conversion.
- Move API keys and remote config out of source code.
- Add app screenshots and Play Store style release notes.

## Acknowledgements

- [AndroidX Media3](https://developer.android.com/media/media3)
- [Jamendo API](https://developer.jamendo.com)
- [Material Components for Android](https://github.com/material-components/material-components-android)
- [Glide](https://github.com/bumptech/glide)
- [Retrofit](https://github.com/square/retrofit)

## License

This project is licensed under the GNU General Public License v3.0 or later. See [LICENSE](LICENSE) for details.

Some files retain their original upstream license headers, including Android Open Source Project Apache-2.0 snippets and GPL-licensed components. Keep those notices intact when redistributing or modifying the project.
