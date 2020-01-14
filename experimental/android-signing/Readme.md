# Android Signing


This demo extends Corda to allow signing on an Android device. This is a basic
implementation of the human interaction (HI) feature.

The private keys are held on the device and don't leave the device, except for
backup purposes.

## Initial Version

An RPC add can drive payment flows.

A signing app is used to utilise the HSM on the Android device.


## Components

* Android app that reveives and displays notification (sign or decline options).
* Flows that interact with the app.
* The Android app can initiate flows via the P2P interface of the Corda node.

## Flow of the Demo

* Corda Node is setup on daywatch, hosting the accounts.
* User Alice creates an account on the corda node (via mobile app or node shell, registers its public key).
* User Bob creates an account on the corda node.
* User Alice issues token and transferes to Bob (can scan Bob's QR code to discover account).
* TODO: when are the sign/decline notifications appearing?

## Corda Features

* HSM API
* Accounts
* Flows

## Android Features

* Cloud messaging (up to 4k payload, https://firebase.google.com/docs/cloud-messaging/)
* Notifications (https://developer.android.com/guide/topics/ui/notifiers/notifications)
* HSM (https://source.android.com/security/keystore, https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec)

## TOOD
* corda serialisation on the device
* rpc client
