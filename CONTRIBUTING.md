# Contributing to OSPdfReader

First off, thank you for considering contributing to OSPdfReader! 🎉

This project exists because of people like you who want to make PDF reading accessible to everyone—**especially students**—without paywalls or subscriptions.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Coding Guidelines](#coding-guidelines)
- [Pull Request Process](#pull-request-process)

---

## Code of Conduct

By participating in this project, you agree to maintain a welcoming and inclusive environment. Be respectful, considerate, and constructive in all interactions.

---

## How Can I Contribute?

### 🐛 Reporting Bugs

Before creating a bug report, please check existing issues to avoid duplicates.

When filing an issue, include:
- **Device information** (manufacturer, model, Android version)
- **App version**
- **Steps to reproduce** the issue
- **Expected vs actual behavior**
- **Screenshots or screen recordings** if applicable
- **Sample PDF** if the issue is document-specific (ensure no sensitive data)

### ✨ Suggesting Features

Feature requests are welcome! Please:
1. Check if the feature has already been requested
2. Open an issue with the `enhancement` label
3. Clearly describe the use case and expected behavior
4. If possible, include mockups or references

### 💻 Code Contributions

Ready to contribute code? Great! Here's how:

1. **Fork** the repository
2. **Create a branch** for your feature/fix: `git checkout -b feature/your-feature-name`
3. **Make your changes** following our coding guidelines
4. **Test thoroughly** on multiple devices if possible
5. **Commit** with clear, descriptive messages
6. **Push** to your fork and **open a Pull Request**

### 📝 Documentation

Help improve our documentation:
- Fix typos or unclear explanations
- Add missing documentation
- Improve code comments
- Create tutorials or guides

---

## Development Setup

### Requirements

- **Android Studio** Hedgehog (2023.1.1) or later
- **JDK 17**
- **Android SDK 34**
- **Git**

### Getting Started

```bash
# Fork and clone the repository
git clone https://github.com/YOUR_USERNAME/OSPdfReader.git
cd OSPdfReader

# Open in Android Studio
# Or build from command line:
./gradlew assembleDebug
```

### Project Structure

```
OSPdfReader/
├── app/
│   ├── src/main/
│   │   ├── java/           # Kotlin source files
│   │   ├── res/            # Resources (layouts, strings, etc.)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
└── build.gradle.kts
```

---

## Coding Guidelines

### Architecture

This project follows **MVVM + Clean Architecture**:
- **UI Layer**: Jetpack Compose + Material 3
- **Domain Layer**: Use cases and business logic
- **Data Layer**: Repositories and data sources

### Style Guide

- **Language**: Kotlin
- **Formatting**: Use Android Studio's default Kotlin style
- **Naming**: Follow Kotlin naming conventions
  - `camelCase` for variables and functions
  - `PascalCase` for classes and interfaces
  - `SCREAMING_SNAKE_CASE` for constants

### Best Practices

- Write **self-documenting code** with clear naming
- Add **KDoc comments** for public APIs
- Keep functions **small and focused**
- Follow **single responsibility principle**
- Write **unit tests** for business logic
- Use **dependency injection** (Hilt) for dependencies

### Commit Messages

Use clear, descriptive commit messages:

```
feat: add bookmark export functionality
fix: resolve crash when opening large PDFs
docs: update installation instructions
refactor: simplify annotation rendering logic
```

Prefix with:
- `feat:` - new feature
- `fix:` - bug fix
- `docs:` - documentation changes
- `refactor:` - code refactoring
- `test:` - adding/updating tests
- `chore:` - maintenance tasks

---

## Pull Request Process

### Before Submitting

- [ ] Code follows the project's coding guidelines
- [ ] Self-reviewed the changes
- [ ] Added/updated tests if applicable
- [ ] Tested on a physical device or emulator
- [ ] Updated documentation if needed
- [ ] No merge conflicts with `main` branch

### PR Template

When opening a PR, include:

1. **Description**: What does this PR do?
2. **Related Issue**: Link to the issue if applicable
3. **Type of Change**: Bug fix, feature, refactor, etc.
4. **Testing**: How was this tested?
5. **Screenshots**: If UI changes are involved

### Review Process

1. A maintainer will review your PR
2. Address any requested changes
3. Once approved, a maintainer will merge the PR
4. Your contribution will be part of OSPdfReader! 🎉

---

## 📬 Questions?

If you have questions, feel free to:
- Open an issue with the `question` label
- Start a GitHub Discussion
- **Email**: [durjoymajumdar02@gmail.com](mailto:durjoymajumdar02@gmail.com)
- **Website**: [asokakrsna.github.io](https://asokakrsna.github.io)

---

## 🙏 Thank You!

Every contribution, no matter how small, helps make OSPdfReader better for everyone. Thank you for being part of this project!

**Made with ❤️ for students and the open source community**
