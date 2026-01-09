# OSPdfReader

A free, open-source Android PDF reader with professional annotation capabilities designed for students.

## Features

- 📖 **PDF Reading** - Smooth page rendering with horizontal swipe (book mode) and vertical scroll
- ✏️ **Annotations** - Pen, highlighter, shapes, text annotations (coming soon)
- 🖊️ **Stylus Support** - Low-latency inking with pressure sensitivity (coming soon)
- 🔍 **Search & OCR** - Full-text search with offline OCR (coming soon)
- ☁️ **Google Drive Sync** - Sync flattened PDFs to Drive (coming soon)
- 🌙 **Dark Mode** - Eye-friendly dark theme

## Building

### Requirements
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Steps

1. Clone the repository:
```bash
git clone https://github.com/yourusername/OSPdfReader.git
cd OSPdfReader
```

2. Open in Android Studio and sync Gradle

3. Build and run:
```bash
./gradlew assembleDebug
```

## Architecture

- **UI**: Jetpack Compose with Material 3
- **PDF Engine**: MuPDF
- **DI**: Hilt
- **Database**: Room (for bookmarks, recent files)
- **Architecture**: MVVM + Clean Architecture

## License

This project is licensed under the AGPL-3.0 License - see the LICENSE file for details.

MuPDF is licensed under AGPL-3.0 © Artifex Software, Inc.

## Contributing

Contributions are welcome! Please read our contributing guidelines before submitting PRs.

## Acknowledgments

- MuPDF for the excellent PDF rendering engine
- Material Design team for the design system
- All the students who inspired this project
