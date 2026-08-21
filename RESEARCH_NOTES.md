# RESEARCH_NOTES.md

These are reference points for the coding agent. Prefer current official documentation when implementation starts.

## Android

Exact alarms / AlarmManager:
https://developer.android.com/reference/android/app/AlarmManager

Exact-alarm permission / special permission:
https://developer.android.com/training/permissions/requesting-special
https://developer.android.com/about/versions/14/changes/schedule-exact-alarms

Manifest permissions:
https://developer.android.com/reference/android/Manifest.permission

NotificationManager / full-screen capability:
https://developer.android.com/reference/android/app/NotificationManager

Offline-first Android architecture:
https://developer.android.com/topic/architecture/data-layer/offline-first

Room:
https://developer.android.com/training/data-storage/room

## Samsung

Galaxy A06:
https://www.samsung.com/tr/smartphones/galaxy-a/galaxy-a06-black-128gb-sm-a065fzkgtur/

Sleeping / Deep sleeping / Never sleeping apps:
https://www.samsung.com/us/support/answer/ANS10003442/
https://developer.samsung.com/mobile/app-management.html

## Firebase

Firestore offline behavior:
https://firebase.google.com/docs/firestore/manage-data/enable-offline

Firestore security:
https://firebase.google.com/docs/firestore/security/insecure-rules
https://firebase.google.com/docs/firestore/security/rules-conditions

Firebase Authentication Android:
https://firebase.google.com/docs/auth/android/start

FCM message lifespan:
https://firebase.google.com/docs/cloud-messaging/customize-messages/setting-message-lifespan

## Research conclusions

- Cloud Firestore supports offline caching/synchronization on Android, but this project still keeps Room as the primary-device canonical local store for medication alarm state.
- Android's architecture guidance recommends a local source of truth for offline-first apps.
- Exact alarms are appropriate for user-facing, time-critical behavior; implementation must follow the current API/permission rules.
- Modern Android can restrict full-screen intents; capability must be checked and a notification fallback must exist.
- Samsung sleeping/deep-sleep behavior can restrict background operations and notifications, so A06 setup must explicitly cover it.
- FCM delivery can be delayed and depends on connectivity; it cannot be used as medication alarm delivery.


## Location / dementia safety

Android background location:
https://developer.android.com/develop/sensors-and-location/location/background

Android location permissions:
https://developer.android.com/develop/sensors-and-location/location/permissions

Android geofencing:
https://developer.android.com/develop/sensors-and-location/location/geofencing

Android LocationManager:
https://developer.android.com/reference/android/location/LocationManager

Alzheimer's Association — Wandering:
https://www.alz.org/help-support/caregiving/safety/wandering

Alzheimer's Association — Technology Safety:
https://www.alz.org/help-support/caregiving/safety/technology-safety-older-adults

Alzheimer's Association — Technology 101:
https://www.alz.org/help-support/caregiving/safety/technology-101

Samsung Galaxy A06 official specifications show GPS, GLONASS, BeiDou and Galileo:
https://www.samsung.com/tr/smartphones/galaxy-a/galaxy-a06-black-128gb-sm-a065fzkgtur/

Key distinction:
GNSS/GPS can provide positioning without network availability, but remote family tracking requires a separate communication path. No cellular/Wi-Fi communication means no live remote location transmission from Galaxy A06 alone.
