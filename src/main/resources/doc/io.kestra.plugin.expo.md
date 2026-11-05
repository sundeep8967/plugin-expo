# Expo

The Expo plugin enables sending push notifications directly to React Native and mobile applications via the [Expo Push Notification Service](https://docs.expo.dev/push-notifications/overview/).

Expo manages delivery to Apple Push Notification service (APNs) on iOS and Firebase Cloud Messaging (FCM) on Android behind a unified HTTP endpoint, removing the need to configure direct APNs certificates or individual FCM server credentials in your backend orchestration flows.

## Getting Started

1. In your React Native app using `expo-notifications`, obtain the recipient device push token via `Notifications.getExpoPushTokenAsync()`. Expo push tokens follow the format `ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]`.
2. If your project has Push Security enabled in the Expo developer dashboard, generate an Expo access token under your project settings and store it in Kestra Secrets under `EXPO_ACCESS_TOKEN`.
3. Dispatch push notifications from your Kestra flows to individual devices or token lists.

## Tasks

### `io.kestra.plugin.expo.PushNotification`

Dispatches push notifications to one or more Expo push tokens:
- `to`: One or more recipient tokens (`ExponentPushToken[...]`).
- `title`: Notification headline.
- `body`: Notification message body.
- `data`: Arbitrary JSON key-value map delivered to the client application.
- `sound`: Sound to play upon arrival (`default` or custom sound asset).
- `badge`: Application badge count icon number.
- `channelId`: Notification channel ID for Android 8.0+.
- `priority`: Delivery priority (`default`, `normal`, or `high`).
- `ttl`: Time-to-live in seconds if the target device is offline.
