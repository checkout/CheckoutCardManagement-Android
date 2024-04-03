# Table of Contents
- [What is the CheckoutCardManagement-Android SDK?](#What-is-the-CheckoutCardManagement-Android-SDK?)
- [Environments](#Environments)
- [Features](#Features)
- [Requirements](#Requirements)
- [Integration](#Integration)
    - [Import SDK](#Import-SDK)
    - [Prepare Card Manager](#Prepare-Card-Manager)
    - [Login user](#Login-user)
    - [Get a list of cards](#Get-a-list-of-cards)
    - [Update card state](#Update-card-state)
    - [Retrieve Secure Data](#Retrieve-secure-data)
    - [Push Provisioning](#Push-provisioning)
- [Contact](#Contact)
***

# What is the CheckoutCardManagement Android SDK?

Our CheckoutCardManagement-Android SDK is the mobile gateway to our wider [card issuing solution](https://www.checkout.com/docs/card-issuing). It enables your mobile application to securely access important card information and functionality, in a fast and safe way.

***

# Environments

The Android SDK supports 2 environments: Sandbox and Production. **CheckoutCardManager** powers the Sandbox and Production environments. Use of these environments requires onboarding with our operations team. During onboarding, you'll receive client credentials, which you will then need to
handle on your backend for authentication. You will be expected to manage [Strong Custom Authentication (SCA) requirements](https://www.checkout.com/docs/payments/regulation-support/sca-compliance-guide) as part of accessing the SDK's functionality.

***

# Features

## Easy to integrate

Your app can consume the SDK directly through Maven Central; no additional setup is required, meaning you can get up and running quickly.

## Developer friendly

We value the Android community and are big fans of following best practices and cutting-edge technology. As such, our APIs utilise [Jetpack Compose](https://developer.android.com/jetpack/compose), [Kotlin Coroutine](https://kotlinlang.org/docs/coroutines-overview.html) and follow the [Kotlin Coding Convention](https://kotlinlang.org/docs/coding-conventions.html), so usage will feel familiar.

## Feature-rich

Whether you're retrieving a list of cards for a cardholder, accessing sensitive card information, or adding a card to the Google wallet, our SDK makes it easy for you to provide this functionality to your users.

## Compliant

Using the SDK keeps you compliant with the [Payment Card Industry Data Security Standards (PCI DSS)](https://www.checkout.com/docs/payments/regulation-support/pci-compliance).

If you have any specific questions about PCI compliance, reach out to your operations contact.

***

# Requirements

The SDK is distributed as a native Android dependency. If you have a hybrid project, review your hybrid platform's documentation for guidance on how to consume native third-party SDKs.

You should have **SCA** enabled for your users. Whilst we take care of in-depth compliance, you are required to perform SCA on your users as requested and documented.

Each authentication session can be used to simultaneously generate multiple tokens for different systems. For example, for sign in, or to get an SDK session token or an internal authentication token. However, only one SDK token can be generated from each SCA flow requested.

***

# Integration

## Import SDK

Use Gradle to import the SDK into your app:

```gradle
// in project build.gradle
repositories {
    mavenCentral()
}

// in app level build.gradle
dependencies {
    // Required for initialise the CardManagementDesignSystem
    implementation "androidx.compose.ui:ui:$compose_ui_version"
    implementation 'com.checkout:checkout-sdk-card-management-android:$checkout_card_management_vesrion'
}
```

## Prepare Card Manager

To start consuming SDK functionality, instantiate the main object, which enables access to the
functionality:

```Kotlin
import androidx.compose.ui.text.TextStyle
import com.checkout.cardmanagement.CheckoutCardManager
import com.checkout.cardmanagement.model.Environment

class YourObject {
  // Customisable UI properties for the secure components delivered by the SDK
  private val cardManagerDesignSystem = CardManagementDesignSystem(
    textStyle = TextStyle(),
    panTextSeparator = "-"
  )

  // The core object through which the SDK's functionality is accessed 
  private val cardManager = CheckoutCardManager(
    context = context,
    designSystem = cardManagerDesignSystem,
    environment = Environment.SANDBOX
  )
}
```

## Login user

In order to provide your users with the SDK functionality, you must authenticate them for the session.

**You are responsible for ensuring you authenticate a user** for the session. This means your application should retrieve a session token from your authentication backend.

`logInSession()` returns a boolean value of the token validation result. `CheckoutCardManager` is allowed to operate `getCards` only if the token is valid.

```Kotlin
cardManager.logInSession("{Token_retrieved_from_your_backend}")
```

## Get a list of cards

Once you've authenticated the cardholder and your application, you can return a list of non-sensitive card data using `getCards` for that cardholder.

This returns the following card details:

- **Last 4 digits of the full card number**, also known as the Primary Account Number (PAN)
- Card's **expiry date**
- **Cardholder's** name
- Card's **state** (inactive, active, suspended, or revoked)
- A **unique ID** for each card returned

```Kotlin
cardManager.getCards { result: Result<List<Card>> ->
  result.onSuccess {
    // You'll receive a list of cards that you can integrate within your UI
    // The card info includes the last 4 digits (PAN), expiry date, cardholder name, card state, and id
  }.onFailure {
    // If something goes wrong, you'll receive an error with more details
  }
}
```

## Update Card State

Card State Management API is an extension function of the `Card` class, so you must first obtain it from the SDK.

Once you have the `Card` object, we would also suggest using the `card.possibleStateChanges` API for an improved user experience. Although it is not a hard requirement, You can request the possible new states from the card object before running the card management operation.

```Kotlin
// This will return a list of possible states that the card can be transitioned to
val possibleNewStates = card.possibleStateChanges

// We can activate the card, if the state was returned by possibleStateChanges
if (possibleNewStates.contains(CardState.ACTIVE)) {
  card.activate(completionHandler)
}

// We can suspend the card, if the state was returned by possibleStateChanges
if (possibleNewStates.contains(CardState.SUSPENDED)) {
  // Pass an optional reason of why suspending card 
  val reason: CardSuspendReason? = CardSuspendReason.LOST
  card.suspend(reason, completionHandler)
}

// We can revoke the card, if the state was returned by possibleStateChanges
if (possibleNewStates.contains(CardState.REVOKED)) {
  // This is a destructive and irreversible action - once revoked, the card cannot be reactivated
  // We recommended that you request UI confirmation that your user intended to perform this action
  // Pass an optional reason of why revoking card
  val reason: CardRevokeReason? = CardRevokeReason.STOLEN
  card.revoke(reason, completionHandler)
}
```

Regardless of the new state requested, the same completion handler can be used:

```Kotlin
fun cardStateChangeCompletionHandler(result: Result<Unit>): Unit {
  result
    .onSuccess {
      // Card state has been updated successfully, and will be reflected by both the backend and the SDK
    }.onFailure {
      // If something goes wrong, you'll receive an error with more details
    }
}
```

Note: card states. There are 4 different card states, which apply to both virtual and physical cards. They are:

| Status    | Description                                                                                                                                                                            |
|-----------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| INACTIVE  | The card is awaiting activation and is unusable until then. By default, physical cards are set to `inactive` on creation. Cards cannot transition to `inactive` from any other status. |
| ACTIVE    | The card can process transactions as normal. By default, virtual cards are set to `active` on creation.                                                                                |
| SUSPENDED | Card has been manually suspended by the cardholder; transactions are temporarily blocked. The card can be reactivated to allow for normal use.                                         |
| REVOKED   | Transactions are permanently blocked. The card cannot be reactivated from this status.                                                                                                 |

## Retrieve Secure Data

<sub> The following example covers PIN, but similar APIs are available for PAN, CVV, and PAN + CVV. The general flow remains the same. The only different is the API for PAN + CVV it will return a pair of views instead of one.</sub>

These calls are subject to a unique SCA flow prior to every individual call. Only on completion of a specific authentication can a single-use token be requested and provided to the SDK, in order to continue executing the request.

```Kotlin
val singleUseToken = "{Single_use_token_retrieved_from_your_backend_after_SCA}"

// Request sensitive data via the card object
card.getPin(singleUseToken) { result: Result<AbstractComposeView> ->
  result
    .onSuccess {
      // If successfully, you'll receive a Compose view contains the secure data that you can display to the user
    }.onFailure {
      // If something goes wrong, you'll receive an error with more details
    }
}
```

The UI component protects the value and safely delivers it to the user as the sole recipient. The UI component design can be adjusted as appropriate when [creating the card manager](#Prepare-card-manager) and providing the `CardManagementDesignSystem`.

### Push Provisioning

**Push Provisioning** is the operation of adding a physical or virtual card to a digital wallet. On Android, this means adding a card to the Google Wallet so that card can be used for Google Pay.

Enabling this operation is highly complex as it requires interaction between multiple entities including you, Checkout.com, Google Pay, and the card scheme (in our case, Mastercard). As such, push provisioning is subject to additional onboarding and can only be tested in your Production environment. For more details on onboarding, please speak to your operations contact.

A typical call may look as follows:

```Kotlin
card.provision(
  activity = theActivityHandleTheProvisionOutcome,
  cardholderID = "{id_of_cardholder_performing_operation}",
  configuration = ProvisioningConfiguration(/* */),
  token = "{specific_token_generated_for_operation}",
  completionHandler = { result: Result<Unit> ->
    // Callback after the operation has completed
  }
)
```

*If you're using the `CheckoutCardManagement` library, calling it without completing proper onboarding will result in an intentional crash.*

***

# Out-of-Band (OOB) Authentication

Our CheckoutCardManagement-Android SDK is compatible with our Out-of-Band (OOB) Authentication solution, which is covered through our [OOB-Android SDK]([https://www.checkout.com/docs/payments/regulation-support/pci-compliance], and you can read integration details on that page. Checkout.com's OOB SDK enables you to seamlessly authenticate your cardholders within your Android app.

# Contact

For Checkout.com issuing clients, please email issuing_operations@checkout.com for any questions.
