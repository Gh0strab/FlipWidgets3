# FlipCoverWidgets

An Android app that allows you to display system widgets on your Samsung Galaxy Z Flip 7's cover screen.

## Features

- **Widget Discovery**: Browse all available Android widgets installed on your device
- **Cover Screen Support**: Creates Samsung-compatible widgets that appear on the Z Flip 7's cover screen
- **Multiple Slots**: Configure up to 4 different widget slots
- **Bitmap Snapshots**: Captures and displays widget content on the cover screen

## How It Works

1. **Select Widgets**: Open the app and tap on a slot (1-4) to select a system widget
2. **Configure**: The app will host the selected widget and capture its visual output
3. **Add to Cover Screen**: 
   - Go to Samsung Settings > Cover screen > Widgets
   - Find "FlipCoverWidget Slot 1" (or 2, 3, 4)
   - Add it to your cover screen
4. **Enjoy**: The widget snapshot will appear on your cover screen

## Technical Details

### Architecture

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material3
- **Database**: Room (for widget configurations)
- **Widget Hosting**: AppWidgetHost API
- **Samsung Integration**: Flex Window API (sub_screen display)

### Limitations

- Widgets are displayed as static bitmap snapshots (not live/interactive)
- Updates happen periodically or when you refresh manually
- Some widgets may not render correctly due to RemoteViews limitations
- Requires Samsung Galaxy Z Flip 7 or compatible foldable with cover screen

## Building

### Prerequisites

- Android SDK API 34
- Java 17+
- Gradle 8.2+

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Build release APK (unsigned)
./gradlew assembleRelease
```

### Installation

1. Build the APK using the commands above
2. Transfer the APK to your Samsung Z Flip 7
3. Enable "Install from Unknown Sources" in Settings
4. Install the APK
5. Grant necessary permissions (BIND_APPWIDGET)

## Usage Instructions

### Setting Up Widgets

1. Open **FlipCoverWidgets** app
2. Tap on **Slot 1** (or any available slot)
3. Select a widget from your installed widgets
4. Configure the widget if prompted
5. The widget will be saved and captured

### Adding to Cover Screen

1. Close your Z Flip 7 to activate cover screen
2. Swipe to access cover screen widgets
3. Tap the **+** icon
4. Go to Samsung **Settings > Cover screen > Widgets**
5. Enable **FlipCoverWidget Slot 1**
6. Add it to your cover screen layout

### Removing Widgets

1. Open the FlipCoverWidgets app
2. Find the configured slot
3. Tap **Remove** button
4. The widget will be unbound and removed

## Permissions

- `BIND_APPWIDGET`: Required to host other apps' widgets
- `POST_NOTIFICATIONS`: For potential update notifications (optional)

## Compatibility

- **Minimum SDK**: API 30 (Android 11)
- **Target SDK**: API 34 (Android 14)
- **Tested On**: Samsung Galaxy Z Flip 7 with One UI 6+

## Known Issues

- Some protected widgets may not be accessible
- High-power widgets might be throttled by Samsung
- Widget snapshots may have slight delay in updates
- Interactive widget elements won't respond to touch
- Process death recovery: Widget rebinding after app restart may require manual refresh in some cases
- Background updates: Currently requires app to be in foreground for optimal snapshot capture

## Future Improvements

- Implement WorkManager for periodic background refresh
- Add manual refresh button within widgets
- Improve process death recovery robustness
- Add widget resize handling for different cover screen layouts
- Support for transparent widget backgrounds
- Implement proper error handling and user feedback

## Contributing

This is an open-source project. Feel free to fork and improve!

## License

This project is provided as-is for educational and personal use.

## Credits

Built with ❤️ for Samsung Galaxy Z Flip users

Based on Samsung's Flex Window API documentation and Android's AppWidget framework.
