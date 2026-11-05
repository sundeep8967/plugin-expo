# Kestra Expo Plugin

The Expo plugin enables sending push notifications directly to React Native and mobile applications via the [Expo Push Notification Service](https://docs.expo.dev/push-notifications/overview/).

Expo manages delivery across Apple Push Notification service (APNs) on iOS and Firebase Cloud Messaging (FCM) on Android through a unified API endpoint, avoiding the need to configure direct APNs certificates or individual FCM server credentials in backend workflows.

## Why

- **Cross-Platform Mobile Notifications**: Send notifications to iOS and Android React Native apps with a single API call using standard Expo push tokens.
- **Zero Native Infrastructure**: Avoids configuring individual Apple developer certificates or Google FCM service keys on orchestration servers.
- **Workflow Automation**: Trigger automated alerts, transaction updates, and transactional push messages directly from Kestra data pipelines.

## What

- Provides tasks under `io.kestra.plugin.expo`.
- Includes `io.kestra.plugin.expo.PushNotification` for sending rich push notifications with badges, sounds, custom data payloads, and priority settings.

## License

Apache 2.0 (c) Kestra Technologies.
