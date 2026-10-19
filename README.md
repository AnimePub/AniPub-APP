#  AniPub

> A clean, fast, mobile-first anime browser powered by **AniPub** data, with built-in ad blocking and offline downloads.

![Platform](https://img.shields.io/badge/platform-Android-blue)
![Status](https://img.shields.io/badge/status-active-brightgreen)
![License](https://img.shields.io/badge/license-GPL3-lightgrey)
--------
Download The App
<p align="center">
  <a href="https://github.com/AnimePub/AniPub-APP/raw/master/app/release/app-release.apk" target="_blank">
    <img src="https://img.shields.io/badge/Download-APK-4CAF50?style=for-the-badge&logo=android&logoColor=white" alt="Download APK" width="220" />
  </a>
</p>


##  Features

| Feature | Description |
|---|---|
|  **Mobile-first UI** | Smooth, touch-friendly browsing designed for phones |
|  **Search & Filters** | Find anime by title, genre, season, or status |
|  **Episode Browser** | Browse seasons, episodes, and details in one place |
|  **Ad Blocking** | Built-in blocker removes popups, banners, and redirects |
| ⬇ **Downloads** | Save episodes for offline viewing |
|  **Watchlist** | Track what you're watching and what's next |
|  **Continue Watching** | Pick up exactly where you left off |
|  **Dark Mode** | Built in Dark Mode ..Easy on the eyes, day or night |

---

## 🧭 How It Works

AniPub doesn't host any content itself. It reads anime metadata from the **AniPub** data source and presents it in a fast, ad-free mobile interface.

```mermaid
flowchart LR
    U[ User] --> A[ AniPub App]
    A --> D{{AniPub Data Source}}
    D -->|Anime info, episodes, metadata| A
    A --> B[ Ad Blocker]
    B --> P[ Playback Engine]
    A --> DL[ Download Manager]
    DL --> S[( Local Storage)]
    P --> U
    S --> P
```

---

##  Architecture

```mermaid
graph TD
    UI[Mobile UI Layer]
    STATE[State Management]
    API[AniPub API Client]
    CACHE[(Local Cache)]
    AD[Ad Blocker Module]
    PLAY[Video Player]
    DLM[Download Manager]
    FS[(Device Storage)]

    UI --> STATE
    STATE --> API
    API --> CACHE
    CACHE --> STATE
    UI --> PLAY
    PLAY --> AD
    DLM --> FS
    PLAY --> FS
    UI --> DLM
```

---

##  User Flow

```mermaid
journey
    title Watching an anime episode in AniPub
    section Discover
      Open app: 5: User
      Search or browse: 4: User
      Pick an anime: 5: User
    section Watch
      Choose episode: 5: User
      Ad blocker runs silently: 5: AniPub
      Stream or play offline: 5: User
    section Save
      Tap download: 4: User
      Episode saved to device: 5: AniPub
```

---

##  Ad Blocking

AniPub filters ads at multiple layers so pages stay fast and clean.

```mermaid
sequenceDiagram
    participant User
    participant App as AniPub App
    participant Filter as Ad Filter
    participant Source as AniPub Data

    User->>App: Open anime page
    App->>Source: Request metadata
    Source-->>App: Content + ad scripts
    App->>Filter: Check each request
    Filter-->>App:  Block ads / trackers
    Filter-->>App:  Allow content
    App-->>User: Clean page
```

Blocked by default:
- Pop-up and pop-under windows
- Banner and overlay ads
- Known tracker domains
- Redirect chains

---

##  Download Flow

```mermaid
stateDiagram-v2
    [*] --> Idle
    Idle --> Queued: Tap download
    Queued --> Downloading: Connection OK
    Queued --> Paused: No network / Wi-Fi only
    Downloading --> Paused: Connection lost
    Paused --> Downloading: Resume
    Downloading --> Completed: File saved
    Downloading --> Failed: Error
    Failed --> Queued: Retry
    Completed --> [*]
```

---

##  Getting Started

### Requirements

- Android 8.0+ or iOS 15+
- Internet connection for first-time browsing
- ~50 MB free space (plus space for downloads)

### Install

```bash
# Clone the repository
git clone https://github.com/AnimePub/AniPub-APP.git
cd anipub

# Install dependencies
build the gradle with Android Studio

# Run on a device or emulator
Press the run button to run this app on your device via usb debugging or wifi debugging 
```



##  Features to Add

- Making The Logo more awesome 
- Making Playlist Sync 

##  Contributing

Contributions are welcome!

1. Fork the repo
2. Create a branch: `git checkout -b feature/amazing-feature`
3. Commit your changes: `git commit -m "Add amazing feature"`
4. Push: `git push origin feature/amazing-feature`
5. Open a Pull Request

---

##  Disclaimer

AniPub is a client app for browsing metadata. It does not host any video content. Users are responsible for complying with copyright laws in their region. Only download content you have the right to access.
-- Will you heed this warning though ? We are pirate by heart .. haha 
---

##  License

Distributed under the GPL3 License. See `LICENSE` for details.

---

<p align="center">Made with ❤️ by AniPub for anime fans on the go</p>