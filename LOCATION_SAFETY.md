# LOCATION_SAFETY.md

## Purpose

Optional future safety module for a grandfather living with dementia/cognitive impairment.

This module is NOT a diagnostic tool and does not determine whether dementia has progressed to Alzheimer's disease.

Its purpose is to reduce risk if the user becomes disoriented or wanders.

## Core physical limitation

A phone has two separate jobs:

1. DETERMINE its position.
2. TRANSMIT that position to another person.

Galaxy A06 supports GNSS location technologies including GPS, GLONASS, BeiDou and Galileo.

GNSS can determine outdoor position without mobile data or Wi-Fi when satellite visibility is adequate.

However:

> If the phone has no mobile signal, no Wi-Fi/internet, and no separate satellite communication uplink, the phone cannot send that live position to a remote family member.

The application cannot solve this limitation in software.

When connectivity returns, locally recorded location events can synchronize.

## Three operating states

### 1. ONLINE

Primary phone:
- obtains location
- can sync location/status to family
- can send geofence exit events
- family can see recent location
- family UI shows accuracy + timestamp

### 2. OFFLINE BUT GNSS AVAILABLE

Primary phone can:
- obtain approximate outdoor coordinates
- evaluate a locally stored safety zone/geofence
- record a breadcrumb/location history locally
- play a local warning/help prompt
- show a large "Aileyi Ara" action
- queue location events for later synchronization

Family phones CANNOT see the new live position until some communication path returns.

Family UI must show:
- last received location
- exact timestamp
- "Cihaz çevrimdışı — canlı konum bilinmiyor"

Never display stale location as current.

### 3. NO GNSS / INDOORS / LOCATION DISABLED

Primary phone may not know its own current position reliably.

Show diagnostics:
- Location permission
- Precise location
- Background location
- Location services enabled
- Last known fix time
- Last location accuracy

Do not pretend location is reliable if it is stale or low-accuracy.

## Recommended safety-zone design

Home/caregiver can configure:
- home point
- safe radius
- optional larger secondary radius

Example:
- Home safe zone: 250 m
- Warning zone: configurable by caregiver

Do not hard-code a radius before testing the village geography.

When the primary device detects an exit:

1. persist SAFETY_ZONE_EXIT locally
2. play a simple local alert
3. show simple grandfather-facing help UI
4. queue remote event
5. if network exists, sync immediately
6. family gets event with location/time/accuracy

Suggested grandfather wording:

"Evden uzaklaştın."
"Anneanneyi ara."

or another phrase tested with the family.

Do not use frightening or accusatory wording.

## Family location screen

Display:

- current status:
  - ONLINE
  - OFFLINE
  - LOCATION_UNAVAILABLE
- last sync time
- last location time
- location accuracy
- map pin if available
- safety-zone status
- battery percentage if synchronized
- last device seen time

Important:
A map pin must always have a timestamp.
If older than a configured threshold, mark it visibly stale.

Never write:
- "Dede şu an burada"

when the latest location may be stale.

Prefer:
- "Son konum — 18:42"
- "Son güncelleme 24 dakika önce"

## Background Android permissions

This feature is separate from medication reminders.

It may require:
- ACCESS_FINE_LOCATION
- ACCESS_BACKGROUND_LOCATION
- foreground service/location behavior if continuous tracking is enabled

Follow current Android 16/API 36 rules when implementing.

Request location permissions only when the family explicitly enables the safety-location feature.

Medication reminders must still work if location permission is denied.

## Battery strategy

Do NOT run maximum-frequency GPS continuously by default.

Prefer layered behavior:

1. low-power/geofence monitoring where reliable
2. event-triggered higher-accuracy fix on zone exit
3. optional "Find now" request while connected
4. short-lived higher-frequency emergency tracking only after a safety event

Continuous high-frequency GNSS can drain battery and may reduce overall safety.

Battery level should be part of caregiver diagnostics.

## Offline breadcrumb history

When enabled, store short-retention local location records:

- timestamp
- latitude/longitude
- accuracy
- source
- safety-zone state

Purpose:
- reconstruct recent movement after connectivity returns
- help family understand the last route

Do not keep indefinite location history by default.

Suggested retention to evaluate:
24-72 hours.

Exact retention is a family privacy decision.

## Nearby-only communication

Bluetooth / Wi-Fi Direct can theoretically communicate without internet, but their range is limited.

They can be considered for:
- "grandmother is nearby" proximity signal
- local pairing/setup

They must NOT be treated as a reliable village-scale lost-person tracking system.

If the grandfather has walked beyond local radio range, Bluetooth does not solve remote tracking.

## SMS fallback

If mobile data is unavailable but cellular/SMS service exists, an optional SMS fallback could send a last known coordinate.

This is NOT useful when the phone has no cellular signal at all.

Do not make SMS a hidden automatic permission.
Design it explicitly and test the carrier/device behavior.

## True no-coverage remote tracking

If the requirement is:

> Family must see his live position even where there is no cellular service and no Wi-Fi.

then Galaxy A06 + this Android app alone is not sufficient.

A separate communication technology is required, for example:
- dedicated satellite-capable tracker/communicator
- another purpose-built off-grid tracking device appropriate for the local environment

Do not claim that GPS alone provides remote tracking.
GPS/GNSS is primarily positioning; remote tracking additionally needs a communication path.

## Dementia safety context

People living with dementia can become lost even in familiar environments.

Location technology is an additional safety layer, not a substitute for:
- caregiver supervision
- home/door safety measures
- identification/contact information
- an agreed missing-person plan
- appropriate medical/care guidance

## Privacy / consent

Location history is highly sensitive.

Design requirements:
- explicitly enabled by caregiver/family
- clearly visible when enabled
- family-scoped access only
- minimum retention
- no third-party analytics
- no selling/sharing
- no public links
- revoke family access immediately when membership is removed

As the user's capacity changes, the family should handle consent/decision-making consistently with their real caregiving/legal situation.

## Recommended implementation order

Do NOT implement this before the medication core is reliable.

Suggested:
- Medication phases 0-12 first
- Location Phase L1: permission + one-shot location prototype on A06
- L2: local safety zone
- L3: offline event persistence
- L4: remote family map while online
- L5: emergency/high-frequency temporary tracking
- L6: battery and physical village testing
- L7: optional SMS fallback evaluation
- L8: decide whether separate dedicated tracker is needed

## Acceptance tests

Test outdoors in the actual village.

- network online + GNSS
- mobile data off + GNSS
- airplane-mode scenario where GNSS remains enabled on device
- no cellular coverage
- indoors
- tree/building obstruction
- phone locked
- app process killed
- battery saver
- Samsung sleeping/deep-sleep behavior
- location permission revoked
- background location revoked
- location services disabled
- stale family map
- reconnect and sync
- geofence exit
- battery impact over a full day

The test report must distinguish:
- location fix obtained locally
- remote family received location
