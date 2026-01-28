# Notification Configuration Guide - CEAP Platform

**Last Updated**: January 27, 2026  
**Audience**: Developers configuring notification delivery for the CEAP platform

---

## Overview

The CEAP platform's **Notification System** delivers engagement requests through multiple channels including email, SMS, push notifications, and in-app messages. This guide covers how to configure delivery channels, templates, opt-out management, frequency capping, and delivery tracking.

**Key Components**:
- **Notification Lambda**: Orchestrates message delivery
- **Channel Adapters**: Interface with delivery providers (SES, SNS, etc.)
- **Template Engine**: Renders personalized messages
- **Opt-Out Manager**: Handles customer preferences
- **Frequency Capper**: Prevents over-messaging
- **Delivery Tracker**: Monitors delivery status

---

## Notification Architecture

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Candidate  │────▶│   Channel    │────▶│   Template   │────▶│   Delivery   │
│   Selection  │     │   Selection  │     │   Rendering  │     │   Provider   │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
       │                    │                     │                    │
       ▼                    ▼                     ▼                    ▼
  Check Opt-Out      Check Frequency      Personalize          Send via SES/SNS
  Check Eligibility  Apply Capping        Add Tracking         Track Status
  Select Channel     Validate Limits      Validate Content     Handle Errors
```

---

## Supported Channels

### 1. Email (Amazon SES)
- Transactional emails
- Marketing emails
- Rich HTML templates
- Attachment support

### 2. SMS (Amazon SNS)
- Text messages
- Short codes
- Long codes
- International support

### 3. Push Notifications (Amazon SNS)
- Mobile push (iOS, Android)
- Web push
- Rich notifications
- Deep linking

### 4. In-App Messages
- Banner messages
- Modal dialogs
- Toast notifications
- Custom UI components

---

## Configuration Steps

### Step 1: Configure Email Channel (SES)

#### 1.1 Basic Email Configuration

Create a notification configuration file `notification-config.json`:

```json
{
  "channels": [
    {
      "channelType": "email",
      "enabled": true,
      "provider": "ses",
      "config": {
        "region": "us-east-1",
        "fromEmail": "reviews@example.com",
        "fromName": "CEAP Platform",
        "replyToEmail": "noreply@example.com",
        "configurationSetName": "ceap-email-tracking",
        "returnPath": "bounces@example.com",
        "maxSendRate": 14,
        "timeout": 10000
      },
      "templates": {
        "product-review-request": {
          "subject": "Share your thoughts on {{productName}}",
          "htmlTemplate": "s3://ceap-templates/email/product-review.html",
          "textTemplate": "s3://ceap-templates/email/product-review.txt"
        }
      }
    }
  ]
}
```

#### 1.2 Advanced Email Configuration with Personalization

```json
{
  "channels": [
    {
      "channelType": "email",
      "enabled": true,
      "provider": "ses",
      "config": {
        "region": "us-east-1",
        "fromEmail": "reviews@example.com",
        "fromName": "CEAP Platform",
        "configurationSetName": "ceap-email-tracking",
        "defaultTags": [
          {"Name": "Program", "Value": "{{programId}}"},
          {"Name": "Marketplace", "Value": "{{marketplace}}"}
        ]
      },
      "templates": {
        "product-review-request": {
          "subject": "Hi {{customerName}}, how was your {{productName}}?",
          "htmlTemplate": "s3://ceap-templates/email/product-review.html",
          "textTemplate": "s3://ceap-templates/email/product-review.txt",
          "variables": [
            "customerName",
            "productName",
            "orderDate",
            "reviewUrl",
            "unsubscribeUrl"
          ],
          "attachments": []
        }
      },
      "personalization": {
        "enabled": true,
        "dataSource": "dynamodb",
        "tableName": "CustomerProfiles-prod",
        "fields": ["preferredName", "language", "timezone"]
      }
    }
  ]
}
```

---

### Step 2: Configure SMS Channel (SNS)

#### 2.1 Basic SMS Configuration

```json
{
  "channels": [
    {
      "channelType": "sms",
      "enabled": true,
      "provider": "sns",
      "config": {
        "region": "us-east-1",
        "senderID": "CEAP",
        "messageType": "Transactional",
        "maxPrice": "0.50",
        "timeout": 5000
      },
      "templates": {
        "product-review-sms": {
          "message": "Hi {{customerName}}! Rate your recent purchase: {{shortUrl}}. Reply STOP to opt out.",
          "maxLength": 160
        }
      }
    }
  ]
}
```

#### 2.2 Advanced SMS Configuration with Country-Specific Settings

```json
{
  "channels": [
    {
      "channelType": "sms",
      "enabled": true,
      "provider": "sns",
      "config": {
        "region": "us-east-1",
        "defaultSenderID": "CEAP",
        "messageType": "Transactional",
        "countrySettings": {
          "US": {
            "senderID": "CEAP",
            "maxPrice": "0.50"
          },
          "UK": {
            "senderID": "CEAP-UK",
            "maxPrice": "0.30"
          },
          "IN": {
            "senderID": "CEAPPL",
            "maxPrice": "0.10"
          }
        }
      },
      "templates": {
        "product-review-sms": {
          "message": "Hi {{customerName}}! Rate {{productName}}: {{shortUrl}}. Text STOP to unsubscribe.",
          "maxLength": 160,
          "urlShortening": {
            "enabled": true,
            "service": "bitly",
            "domain": "ceap.link"
          }
        }
      }
    }
  ]
}
```

---

### Step 3: Configure Push Notifications

#### 3.1 Mobile Push Configuration

```json
{
  "channels": [
    {
      "channelType": "push",
      "enabled": true,
      "provider": "sns",
      "config": {
        "region": "us-east-1",
        "platformApplications": {
          "ios": "arn:aws:sns:us-east-1:123456789:app/APNS/CeapApp",
          "android": "arn:aws:sns:us-east-1:123456789:app/GCM/CeapApp"
        },
        "timeout": 5000
      },
      "templates": {
        "product-review-push": {
          "title": "Rate your purchase",
          "body": "How was your {{productName}}? Share your feedback!",
          "data": {
            "programId": "{{programId}}",
            "subjectId": "{{subjectId}}",
            "deepLink": "ceap://review/{{subjectId}}"
          },
          "badge": 1,
          "sound": "default"
        }
      }
    }
  ]
}
```

#### 3.2 Rich Push Notifications

```json
{
  "channels": [
    {
      "channelType": "push",
      "enabled": true,
      "provider": "sns",
      "config": {
        "region": "us-east-1",
        "platformApplications": {
          "ios": "arn:aws:sns:us-east-1:123456789:app/APNS/CeapApp",
          "android": "arn:aws:sns:us-east-1:123456789:app/GCM/CeapApp"
        }
      },
      "templates": {
        "product-review-rich-push": {
          "title": "Rate {{productName}}",
          "body": "Your order arrived! Share your experience.",
          "image": "{{productImageUrl}}",
          "actions": [
            {
              "id": "rate_now",
              "title": "Rate Now",
              "deepLink": "ceap://review/{{subjectId}}"
            },
            {
              "id": "remind_later",
              "title": "Remind Me Later"
            }
          ],
          "data": {
            "programId": "{{programId}}",
            "subjectId": "{{subjectId}}",
            "customerId": "{{customerId}}"
          }
        }
      }
    }
  ]
}
```

---

### Step 4: Configure In-App Messages

#### 4.1 Basic In-App Configuration

```json
{
  "channels": [
    {
      "channelType": "in-app",
      "enabled": true,
      "provider": "custom",
      "config": {
        "deliveryMethod": "api",
        "endpoint": "https://api.example.com/in-app-messages",
        "apiKey": "${IN_APP_API_KEY}",
        "timeout": 3000
      },
      "templates": {
        "product-review-banner": {
          "type": "banner",
          "position": "top",
          "message": "Rate your recent purchase of {{productName}}",
          "actionButton": {
            "text": "Rate Now",
            "action": "navigate",
            "target": "/review/{{subjectId}}"
          },
          "dismissible": true,
          "displayDuration": 10000
        }
      }
    }
  ]
}
```

---

### Step 5: Configure Templates

#### 5.1 Email Template (HTML)

Create `product-review.html`:

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Product Review Request</title>
    <style>
        body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
        .container { max-width: 600px; margin: 0 auto; padding: 20px; }
        .header { background: #007bff; color: white; padding: 20px; text-align: center; }
        .content { padding: 30px 20px; }
        .button { display: inline-block; padding: 12px 30px; background: #28a745; 
                  color: white; text-decoration: none; border-radius: 5px; }
        .footer { padding: 20px; text-align: center; font-size: 12px; color: #666; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>How was your purchase?</h1>
        </div>
        <div class="content">
            <p>Hi {{customerName}},</p>
            <p>Thank you for your recent purchase of <strong>{{productName}}</strong> on {{orderDate}}.</p>
            <p>We'd love to hear your feedback! Your review helps other customers make informed decisions.</p>
            <p style="text-align: center; margin: 30px 0;">
                <a href="{{reviewUrl}}" class="button">Write a Review</a>
            </p>
            <p>This should only take a minute, and your insights are invaluable to us and the community.</p>
            <p>Thank you for being a valued customer!</p>
        </div>
        <div class="footer">
            <p>If you no longer wish to receive these emails, <a href="{{unsubscribeUrl}}">unsubscribe here</a>.</p>
            <p>&copy; 2026 CEAP Platform. All rights reserved.</p>
        </div>
    </div>
</body>
</html>
```

#### 5.2 Email Template (Plain Text)

Create `product-review.txt`:

```text
Hi {{customerName}},

Thank you for your recent purchase of {{productName}} on {{orderDate}}.

We'd love to hear your feedback! Your review helps other customers make informed decisions.

Write a review: {{reviewUrl}}

This should only take a minute, and your insights are invaluable to us and the community.

Thank you for being a valued customer!

---
If you no longer wish to receive these emails, unsubscribe here: {{unsubscribeUrl}}
© 2026 CEAP Platform. All rights reserved.
```

#### 5.3 Upload Templates to S3

```bash
# Upload HTML template
aws s3 cp product-review.html s3://ceap-templates/email/product-review.html

# Upload text template
aws s3 cp product-review.txt s3://ceap-templates/email/product-review.txt

# Set public read permissions (if needed)
aws s3api put-object-acl \
  --bucket ceap-templates \
  --key email/product-review.html \
  --acl private
```

---

### Step 6: Configure Opt-Out Management

#### 6.1 Opt-Out Configuration

```json
{
  "optOutConfig": {
    "enabled": true,
    "storage": "dynamodb",
    "tableName": "CustomerPreferences-prod",
    "channels": {
      "email": {
        "globalOptOut": true,
        "programSpecific": true,
        "unsubscribeUrl": "https://example.com/unsubscribe?token={{token}}"
      },
      "sms": {
        "globalOptOut": true,
        "keywords": ["STOP", "UNSUBSCRIBE", "CANCEL", "END", "QUIT"],
        "confirmationMessage": "You have been unsubscribed. Reply START to resubscribe."
      },
      "push": {
        "globalOptOut": true,
        "programSpecific": true
      }
    },
    "gracePeriod": 24
  }
}
```

#### 6.2 Opt-Out Table Schema

```bash
# Create CustomerPreferences table
aws dynamodb create-table \
  --table-name CustomerPreferences-prod \
  --attribute-definitions \
    AttributeName=PK,AttributeType=S \
    AttributeName=SK,AttributeType=S \
  --key-schema \
    AttributeName=PK,KeyType=HASH \
    AttributeName=SK,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST

# Schema:
# PK: CUST#{customerId}
# SK: PREF#{channel}#{programId}
# Attributes:
#   - optedOut (Boolean)
#   - optOutDate (String)
#   - optOutReason (String)
```

---

### Step 7: Configure Frequency Capping

#### 7.1 Frequency Cap Configuration

```json
{
  "frequencyCapping": {
    "enabled": true,
    "rules": [
      {
        "ruleId": "daily-email-cap",
        "channel": "email",
        "maxMessages": 2,
        "timeWindow": "24h",
        "scope": "global"
      },
      {
        "ruleId": "weekly-sms-cap",
        "channel": "sms",
        "maxMessages": 3,
        "timeWindow": "7d",
        "scope": "global"
      },
      {
        "ruleId": "program-specific-cap",
        "channel": "email",
        "maxMessages": 1,
        "timeWindow": "30d",
        "scope": "program",
        "programId": "product-reviews"
      }
    ],
    "storage": "dynamodb",
    "tableName": "MessageFrequency-prod",
    "onCapExceeded": "skip",
    "gracePeriod": 1
  }
}
```

#### 7.2 Frequency Tracking Table

```bash
# Create MessageFrequency table
aws dynamodb create-table \
  --table-name MessageFrequency-prod \
  --attribute-definitions \
    AttributeName=PK,AttributeType=S \
    AttributeName=SK,AttributeType=S \
  --key-schema \
    AttributeName=PK,KeyType=HASH \
    AttributeName=SK,KeyType=RANGE \
  --billing-mode PAY_PER_REQUEST

# Enable TTL for automatic cleanup
aws dynamodb update-time-to-live \
  --table-name MessageFrequency-prod \
  --time-to-live-specification \
    Enabled=true,AttributeName=expiresAt

# Schema:
# PK: CUST#{customerId}
# SK: FREQ#{channel}#{timestamp}
# Attributes:
#   - programId (String)
#   - sentAt (String)
#   - expiresAt (Number) - TTL
```

---

### Step 8: Configure Delivery Tracking

#### 8.1 Tracking Configuration

```json
{
  "deliveryTracking": {
    "enabled": true,
    "trackingEvents": [
      "sent",
      "delivered",
      "opened",
      "clicked",
      "bounced",
      "complained"
    ],
    "storage": "dynamodb",
    "tableName": "DeliveryTracking-prod",
    "sesConfigurationSet": "ceap-email-tracking",
    "snsTopicArn": "arn:aws:sns:us-east-1:123456789:ceap-delivery-events",
    "webhookUrl": "https://api.example.com/webhooks/delivery",
    "retentionDays": 90
  }
}
```

#### 8.2 SES Configuration Set

```bash
# Create SES configuration set
aws ses create-configuration-set \
  --configuration-set Name=ceap-email-tracking

# Add SNS destination for tracking
aws ses create-configuration-set-event-destination \
  --configuration-set-name ceap-email-tracking \
  --event-destination '{
    "Name": "ceap-tracking-destination",
    "Enabled": true,
    "MatchingEventTypes": ["send", "delivery", "open", "click", "bounce", "complaint"],
    "SNSDestination": {
      "TopicARN": "arn:aws:sns:us-east-1:123456789:ceap-delivery-events"
    }
  }'
```

---

## AWS CLI Setup Commands

### Configure SES

```bash
# Verify email domain
aws ses verify-domain-identity --domain example.com

# Get verification token
aws ses get-identity-verification-attributes \
  --identities example.com

# Add DKIM
aws ses verify-domain-dkim --domain example.com

# Set up sending limits
aws ses get-send-quota

# Request production access (if in sandbox)
# Submit request through AWS Console
```

### Configure SNS for SMS

```bash
# Set SMS attributes
aws sns set-sms-attributes \
  --attributes '{
    "DefaultSMSType": "Transactional",
    "DefaultSenderID": "CEAP",
    "MonthlySpendLimit": "100"
  }'

# Create SNS topic for delivery events
aws sns create-topic --name ceap-delivery-events

# Subscribe Lambda to topic
aws sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:123456789:ceap-delivery-events \
  --protocol lambda \
  --notification-endpoint arn:aws:lambda:us-east-1:123456789:function:CeapDeliveryTracker
```

### Configure SNS for Push

```bash
# Create platform application for iOS
aws sns create-platform-application \
  --name CeapApp-iOS \
  --platform APNS \
  --attributes '{
    "PlatformCredential": "YOUR_APNS_CERTIFICATE",
    "PlatformPrincipal": "YOUR_APNS_PRIVATE_KEY"
  }'

# Create platform application for Android
aws sns create-platform-application \
  --name CeapApp-Android \
  --platform GCM \
  --attributes '{
    "PlatformCredential": "YOUR_FCM_SERVER_KEY"
  }'
```

---

## Code Examples

### Email Sender (Kotlin)

```kotlin
// ceap-notifications/src/main/kotlin/com/ceap/notifications/EmailSender.kt
class EmailSender(
    private val sesClient: SesClient,
    private val templateEngine: TemplateEngine,
    private val config: EmailChannelConfig
) {
    
    suspend fun sendEmail(
        recipient: String,
        templateId: String,
        variables: Map<String, String>
    ): SendResult {
        val template = config.templates[templateId]
            ?: return SendResult.failure("Template not found: $templateId")
        
        // Render template
        val subject = templateEngine.render(template.subject, variables)
        val htmlBody = templateEngine.renderFromS3(template.htmlTemplate, variables)
        val textBody = templateEngine.renderFromS3(template.textTemplate, variables)
        
        return try {
            val response = sesClient.sendEmail {
                source = "${config.fromName} <${config.fromEmail}>"
                destination {
                    toAddresses = listOf(recipient)
                }
                message {
                    subject {
                        data = subject
                        charset = "UTF-8"
                    }
                    body {
                        html {
                            data = htmlBody
                            charset = "UTF-8"
                        }
                        text {
                            data = textBody
                            charset = "UTF-8"
                        }
                    }
                }
                configurationSetName = config.configurationSetName
                tags = buildTags(variables)
            }
            
            SendResult.success(response.messageId())
        } catch (e: Exception) {
            logger.error("Failed to send email", e)
            SendResult.failure(e.message)
        }
    }
    
    private fun buildTags(variables: Map<String, String>): List<MessageTag> {
        return config.defaultTags.map { tag ->
            MessageTag.builder()
                .name(tag.name)
                .value(templateEngine.render(tag.value, variables))
                .build()
        }
    }
}
```

### SMS Sender (Kotlin)

```kotlin
// ceap-notifications/src/main/kotlin/com/ceap/notifications/SmsSender.kt
class SmsSender(
    private val snsClient: SnsClient,
    private val templateEngine: TemplateEngine,
    private val config: SmsChannelConfig
) {
    
    suspend fun sendSms(
        phoneNumber: String,
        templateId: String,
        variables: Map<String, String>
    ): SendResult {
        val template = config.templates[templateId]
            ?: return SendResult.failure("Template not found: $templateId")
        
        // Render message
        val message = templateEngine.render(template.message, variables)
        
        // Validate length
        if (message.length > template.maxLength) {
            return SendResult.failure("Message exceeds max length: ${message.length} > ${template.maxLength}")
        }
        
        return try {
            val response = snsClient.publish {
                phoneNumber = phoneNumber
                message = message
                messageAttributes = mapOf(
                    "AWS.SNS.SMS.SenderID" to MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(config.senderID)
                        .build(),
                    "AWS.SNS.SMS.SMSType" to MessageAttributeValue.builder()
                        .dataType("String")
                        .stringValue(config.messageType)
                        .build()
                )
            }
            
            SendResult.success(response.messageId())
        } catch (e: Exception) {
            logger.error("Failed to send SMS", e)
            SendResult.failure(e.message)
        }
    }
}
```

### Push Notification Sender (Kotlin)

```kotlin
// ceap-notifications/src/main/kotlin/com/ceap/notifications/PushSender.kt
class PushSender(
    private val snsClient: SnsClient,
    private val templateEngine: TemplateEngine,
    private val config: PushChannelConfig
) {
    
    suspend fun sendPush(
        deviceToken: String,
        platform: String,
        templateId: String,
        variables: Map<String, String>
    ): SendResult {
        val template = config.templates[templateId]
            ?: return SendResult.failure("Template not found: $templateId")
        
        // Create endpoint if not exists
        val endpointArn = createOrGetEndpoint(deviceToken, platform)
        
        // Build platform-specific payload
        val payload = when (platform.lowercase()) {
            "ios" -> buildApnsPayload(template, variables)
            "android" -> buildGcmPayload(template, variables)
            else -> return SendResult.failure("Unsupported platform: $platform")
        }
        
        return try {
            val response = snsClient.publish {
                targetArn = endpointArn
                message = payload
                messageStructure = "json"
            }
            
            SendResult.success(response.messageId())
        } catch (e: Exception) {
            logger.error("Failed to send push notification", e)
            SendResult.failure(e.message)
        }
    }
    
    private suspend fun createOrGetEndpoint(
        deviceToken: String,
        platform: String
    ): String {
        val platformAppArn = config.platformApplications[platform]
            ?: throw IllegalArgumentException("Platform not configured: $platform")
        
        return try {
            val response = snsClient.createPlatformEndpoint {
                platformApplicationArn = platformAppArn
                token = deviceToken
            }
            response.endpointArn()
        } catch (e: Exception) {
            // Endpoint might already exist, retrieve it
            // Implementation depends on your endpoint management strategy
            throw e
        }
    }
    
    private fun buildApnsPayload(
        template: PushTemplate,
        variables: Map<String, String>
    ): String {
        val title = templateEngine.render(template.title, variables)
        val body = templateEngine.render(template.body, variables)
        
        return Json.encodeToString(
            mapOf(
                "APNS" to Json.encodeToString(
                    mapOf(
                        "aps" to mapOf(
                            "alert" to mapOf(
                                "title" to title,
                                "body" to body
                            ),
                            "badge" to template.badge,
                            "sound" to template.sound
                        ),
                        "data" to template.data.mapValues { (_, v) ->
                            templateEngine.render(v, variables)
                        }
                    )
                )
            )
        )
    }
    
    private fun buildGcmPayload(
        template: PushTemplate,
        variables: Map<String, String>
    ): String {
        val title = templateEngine.render(template.title, variables)
        val body = templateEngine.render(template.body, variables)
        
        return Json.encodeToString(
            mapOf(
                "GCM" to Json.encodeToString(
                    mapOf(
                        "notification" to mapOf(
                            "title" to title,
                            "body" to body,
                            "sound" to template.sound
                        ),
                        "data" to template.data.mapValues { (_, v) ->
                            templateEngine.render(v, variables)
                        }
                    )
                )
            )
        )
    }
}
```

### Opt-Out Manager (Kotlin)

```kotlin
// ceap-notifications/src/main/kotlin/com/ceap/notifications/OptOutManager.kt
class OptOutManager(
    private val dynamoDbClient: DynamoDbClient,
    private val config: OptOutConfig
) {
    
    suspend fun isOptedOut(
        customerId: String,
        channel: String,
        programId: String? = null
    ): Boolean {
        // Check global opt-out
        val globalOptOut = checkOptOut(customerId, channel, null)
        if (globalOptOut) return true
        
        // Check program-specific opt-out
        if (programId != null && config.channels[channel]?.programSpecific == true) {
            return checkOptOut(customerId, channel, programId)
        }
        
        return false
    }
    
    private suspend fun checkOptOut(
        customerId: String,
        channel: String,
        programId: String?
    ): Boolean {
        val sk = if (programId != null) {
            "PREF#$channel#$programId"
        } else {
            "PREF#$channel#GLOBAL"
        }
        
        val response = dynamoDbClient.getItem {
            tableName = config.tableName
            key = mapOf(
                "PK" to AttributeValue.builder().s("CUST#$customerId").build(),
                "SK" to AttributeValue.builder().s(sk).build()
            )
        }
        
        return response.item()
            ?.get("optedOut")
            ?.bool() ?: false
    }
    
    suspend fun recordOptOut(
        customerId: String,
        channel: String,
        programId: String? = null,
        reason: String? = null
    ) {
        val sk = if (programId != null) {
            "PREF#$channel#$programId"
        } else {
            "PREF#$channel#GLOBAL"
        }
        
        dynamoDbClient.putItem {
            tableName = config.tableName
            item = mapOf(
                "PK" to AttributeValue.builder().s("CUST#$customerId").build(),
                "SK" to AttributeValue.builder().s(sk).build(),
                "optedOut" to AttributeValue.builder().bool(true).build(),
                "optOutDate" to AttributeValue.builder().s(Instant.now().toString()).build(),
                "optOutReason" to AttributeValue.builder().s(reason ?: "user_request").build()
            )
        }
    }
}
```

### Frequency Capper (Kotlin)

```kotlin
// ceap-notifications/src/main/kotlin/com/ceap/notifications/FrequencyCapper.kt
class FrequencyCapper(
    private val dynamoDbClient: DynamoDbClient,
    private val config: FrequencyCappingConfig
) {
    
    suspend fun canSend(
        customerId: String,
        channel: String,
        programId: String
    ): Boolean {
        val applicableRules = config.rules.filter { rule ->
            rule.channel == channel &&
            (rule.scope == "global" || (rule.scope == "program" && rule.programId == programId))
        }
        
        for (rule in applicableRules) {
            val count = getMessageCount(customerId, channel, rule)
            if (count >= rule.maxMessages) {
                logger.info("Frequency cap exceeded for customer $customerId, rule ${rule.ruleId}")
                return false
            }
        }
        
        return true
    }
    
    private suspend fun getMessageCount(
        customerId: String,
        channel: String,
        rule: FrequencyRule
    ): Int {
        val windowStart = Instant.now().minus(parseTimeWindow(rule.timeWindow))
        
        val response = dynamoDbClient.query {
            tableName = config.tableName
            keyConditionExpression = "PK = :pk AND SK > :sk"
            expressionAttributeValues = mapOf(
                ":pk" to AttributeValue.builder().s("CUST#$customerId").build(),
                ":sk" to AttributeValue.builder().s("FREQ#$channel#${windowStart.toEpochMilli()}").build()
            )
        }
        
        return response.count()
    }
    
    suspend fun recordSent(
        customerId: String,
        channel: String,
        programId: String
    ) {
        val timestamp = Instant.now()
        val expiresAt = timestamp.plus(30, ChronoUnit.DAYS).epochSecond
        
        dynamoDbClient.putItem {
            tableName = config.tableName
            item = mapOf(
                "PK" to AttributeValue.builder().s("CUST#$customerId").build(),
                "SK" to AttributeValue.builder().s("FREQ#$channel#${timestamp.toEpochMilli()}").build(),
                "programId" to AttributeValue.builder().s(programId).build(),
                "sentAt" to AttributeValue.builder().s(timestamp.toString()).build(),
                "expiresAt" to AttributeValue.builder().n(expiresAt.toString()).build()
            )
        }
    }
    
    private fun parseTimeWindow(window: String): Duration {
        val amount = window.dropLast(1).toLong()
        return when (window.last()) {
            'h' -> Duration.ofHours(amount)
            'd' -> Duration.ofDays(amount)
            'w' -> Duration.ofDays(amount * 7)
            else -> Duration.ofDays(1)
        }
    }
}
```

---

## Testing Procedures

### Test 1: Send Test Email

```bash
# Send test email via SES
aws ses send-email \
  --from "reviews@example.com" \
  --destination "ToAddresses=test@example.com" \
  --message '{
    "Subject": {
      "Data": "Test Email from CEAP",
      "Charset": "UTF-8"
    },
    "Body": {
      "Text": {
        "Data": "This is a test email from the CEAP platform.",
        "Charset": "UTF-8"
      }
    }
  }' \
  --configuration-set-name ceap-email-tracking
```

### Test 2: Send Test SMS

```bash
# Send test SMS via SNS
aws sns publish \
  --phone-number "+1234567890" \
  --message "Test SMS from CEAP platform" \
  --message-attributes '{
    "AWS.SNS.SMS.SenderID": {
      "DataType": "String",
      "StringValue": "CEAP"
    },
    "AWS.SNS.SMS.SMSType": {
      "DataType": "String",
      "StringValue": "Transactional"
    }
  }'
```

### Test 3: Test Push Notification

```bash
# Create test endpoint
ENDPOINT_ARN=$(aws sns create-platform-endpoint \
  --platform-application-arn arn:aws:sns:us-east-1:123456789:app/APNS/CeapApp \
  --token "test-device-token-12345" \
  --query 'EndpointArn' \
  --output text)

# Send test push
aws sns publish \
  --target-arn $ENDPOINT_ARN \
  --message '{
    "APNS": "{\"aps\":{\"alert\":{\"title\":\"Test\",\"body\":\"Test push from CEAP\"},\"sound\":\"default\"}}"
  }' \
  --message-structure json
```

### Test 4: Test Opt-Out

```bash
# Record opt-out
aws dynamodb put-item \
  --table-name CustomerPreferences-prod \
  --item '{
    "PK": {"S": "CUST#TEST-001"},
    "SK": {"S": "PREF#email#GLOBAL"},
    "optedOut": {"BOOL": true},
    "optOutDate": {"S": "2026-01-27T12:00:00Z"},
    "optOutReason": {"S": "user_request"}
  }'

# Check opt-out status
aws dynamodb get-item \
  --table-name CustomerPreferences-prod \
  --key '{
    "PK": {"S": "CUST#TEST-001"},
    "SK": {"S": "PREF#email#GLOBAL"}
  }'
```

### Test 5: Test Frequency Cap

```bash
# Record sent messages
for i in {1..3}; do
  TIMESTAMP=$(($(date +%s%3N)))
  aws dynamodb put-item \
    --table-name MessageFrequency-prod \
    --item "{
      \"PK\": {\"S\": \"CUST#TEST-001\"},
      \"SK\": {\"S\": \"FREQ#email#$TIMESTAMP\"},
      \"programId\": {\"S\": \"product-reviews\"},
      \"sentAt\": {\"S\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}
    }"
  sleep 1
done

# Query message count
aws dynamodb query \
  --table-name MessageFrequency-prod \
  --key-condition-expression "PK = :pk" \
  --expression-attribute-values '{
    ":pk": {"S": "CUST#TEST-001"}
  }' \
  --select COUNT
```

---

## Troubleshooting

### Issue: "Email bounces"

**Symptoms**: High bounce rate

**Solutions**:
1. Verify email addresses before sending
2. Use double opt-in for subscriptions
3. Monitor bounce notifications
4. Remove invalid addresses from list

```bash
# Check bounce rate
aws cloudwatch get-metric-statistics \
  --namespace AWS/SES \
  --metric-name Reputation.BounceRate \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Average
```

### Issue: "SMS delivery failures"

**Symptoms**: SMS not delivered

**Solutions**:
1. Verify phone number format (E.164)
2. Check country-specific restrictions
3. Verify sender ID registration
4. Monitor delivery status

```bash
# Check SMS delivery metrics
aws cloudwatch get-metric-statistics \
  --namespace AWS/SNS \
  --metric-name NumberOfNotificationsFailed \
  --dimensions Name=TopicName,Value=ceap-sms-delivery \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Sum
```

### Issue: "Push notification not received"

**Symptoms**: Push notifications not appearing on devices

**Solutions**:
1. Verify device token is valid
2. Check platform application credentials
3. Verify app has notification permissions
4. Test with different devices

```bash
# Check endpoint status
aws sns get-endpoint-attributes \
  --endpoint-arn arn:aws:sns:us-east-1:123456789:endpoint/APNS/CeapApp/device-token

# Update endpoint if disabled
aws sns set-endpoint-attributes \
  --endpoint-arn arn:aws:sns:us-east-1:123456789:endpoint/APNS/CeapApp/device-token \
  --attributes Enabled=true
```

### Issue: "Template rendering errors"

**Symptoms**: Variables not replaced in messages

**Solutions**:
1. Verify variable names match template
2. Check template syntax
3. Ensure all required variables provided
4. Test template rendering separately

```kotlin
// Test template rendering
val template = "Hi {{customerName}}, rate {{productName}}"
val variables = mapOf(
    "customerName" to "John",
    "productName" to "Widget"
)
val rendered = templateEngine.render(template, variables)
println(rendered)  // Should output: "Hi John, rate Widget"
```

### Issue: "Frequency cap not working"

**Symptoms**: Customers receiving too many messages

**Solutions**:
1. Verify frequency rules are enabled
2. Check time window calculations
3. Ensure recordSent is called after delivery
4. Review rule scope (global vs program)

```bash
# Check frequency records
aws dynamodb query \
  --table-name MessageFrequency-prod \
  --key-condition-expression "PK = :pk" \
  --expression-attribute-values '{
    ":pk": {"S": "CUST#12345"}
  }' \
  --scan-index-forward false \
  --limit 10
```

---

## Real-World Use Cases

### Use Case 1: Product Review Email Campaign

**Scenario**: Send personalized review requests after delivery

**Configuration**:
```json
{
  "channels": [
    {
      "channelType": "email",
      "enabled": true,
      "templates": {
        "product-review-request": {
          "subject": "How was your {{productName}}?",
          "htmlTemplate": "s3://ceap-templates/email/product-review.html"
        }
      }
    }
  ],
  "frequencyCapping": {
    "rules": [
      {
        "ruleId": "review-email-cap",
        "channel": "email",
        "maxMessages": 1,
        "timeWindow": "30d",
        "scope": "program",
        "programId": "product-reviews"
      }
    ]
  }
}
```

### Use Case 2: Multi-Channel Video Rating

**Scenario**: Send rating requests via email and push

**Configuration**:
```json
{
  "channels": [
    {
      "channelType": "email",
      "enabled": true,
      "priority": 1,
      "templates": {
        "video-rating-email": {
          "subject": "Rate {{videoTitle}}",
          "htmlTemplate": "s3://ceap-templates/email/video-rating.html"
        }
      }
    },
    {
      "channelType": "push",
      "enabled": true,
      "priority": 2,
      "templates": {
        "video-rating-push": {
          "title": "Rate {{videoTitle}}",
          "body": "Share your thoughts on the video you just watched!"
        }
      }
    }
  ],
  "channelSelection": {
    "strategy": "priority",
    "fallback": true
  }
}
```

### Use Case 3: SMS Survey with Opt-Out

**Scenario**: Send SMS surveys with proper opt-out handling

**Configuration**:
```json
{
  "channels": [
    {
      "channelType": "sms",
      "enabled": true,
      "templates": {
        "service-survey-sms": {
          "message": "Rate your service: {{surveyUrl}}. Reply STOP to opt out."
        }
      }
    }
  ],
  "optOutConfig": {
    "enabled": true,
    "channels": {
      "sms": {
        "keywords": ["STOP", "UNSUBSCRIBE"],
        "confirmationMessage": "You're unsubscribed. Reply START to resubscribe."
      }
    }
  }
}
```

### Use Case 4: In-App Banner with Frequency Control

**Scenario**: Show in-app review prompts without annoying users

**Configuration**:
```json
{
  "channels": [
    {
      "channelType": "in-app",
      "enabled": true,
      "templates": {
        "review-banner": {
          "type": "banner",
          "message": "Enjoying {{productName}}? Leave a review!",
          "displayDuration": 10000
        }
      }
    }
  ],
  "frequencyCapping": {
    "rules": [
      {
        "ruleId": "in-app-banner-cap",
        "channel": "in-app",
        "maxMessages": 3,
        "timeWindow": "7d",
        "scope": "global"
      }
    ]
  }
}
```

---

## Performance Optimization Tips

### 1. Batch Email Sending

```kotlin
// Send emails in batches
suspend fun sendBatchEmails(
    recipients: List<EmailRecipient>
): BatchSendResult {
    val batches = recipients.chunked(50)  // SES bulk email limit
    
    return batches.map { batch ->
        sesClient.sendBulkTemplatedEmail {
            source = config.fromEmail
            template = "product-review-request"
            defaultTemplateData = "{}"
            destinations = batch.map { recipient ->
                BulkEmailDestination.builder()
                    .destination { toAddresses = listOf(recipient.email) }
                    .replacementTemplateData(Json.encodeToString(recipient.variables))
                    .build()
            }
        }
    }.flatten()
}
```

### 2. Async Delivery

```kotlin
// Send notifications asynchronously
suspend fun sendNotificationsAsync(
    candidates: List<Candidate>
): List<SendResult> = coroutineScope {
    candidates.map { candidate ->
        async {
            sendNotification(candidate)
        }
    }.awaitAll()
}
```

### 3. Template Caching

```kotlin
class CachedTemplateEngine(
    private val s3Client: S3Client,
    private val cache: MutableMap<String, String> = ConcurrentHashMap()
) {
    suspend fun renderFromS3(
        templateUrl: String,
        variables: Map<String, String>
    ): String {
        val template = cache.getOrPut(templateUrl) {
            loadTemplateFromS3(templateUrl)
        }
        return render(template, variables)
    }
}
```

### 4. Connection Pooling

```kotlin
// Configure SES client with connection pooling
val sesClient = SesClient.builder()
    .region(Region.US_EAST_1)
    .httpClient(
        ApacheHttpClient.builder()
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(5))
            .socketTimeout(Duration.ofSeconds(30))
            .build()
    )
    .build()
```

---

## Monitoring and Metrics

### Key Metrics to Track

```bash
# Email delivery rate
aws cloudwatch get-metric-statistics \
  --namespace AWS/SES \
  --metric-name Delivery \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Sum

# Email open rate
aws cloudwatch get-metric-statistics \
  --namespace CEAP/Notifications \
  --metric-name EmailOpenRate \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Average

# SMS delivery success
aws cloudwatch get-metric-statistics \
  --namespace AWS/SNS \
  --metric-name NumberOfMessagesPublished \
  --dimensions Name=TopicName,Value=ceap-sms-delivery \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Sum

# Push notification delivery
aws cloudwatch get-metric-statistics \
  --namespace CEAP/Notifications \
  --metric-name PushDeliveryRate \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Average

# Opt-out rate
aws cloudwatch get-metric-statistics \
  --namespace CEAP/Notifications \
  --metric-name OptOutRate \
  --dimensions Name=Channel,Value=email \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Average

# Frequency cap hits
aws cloudwatch get-metric-statistics \
  --namespace CEAP/Notifications \
  --metric-name FrequencyCapHits \
  --start-time 2026-01-27T00:00:00Z \
  --end-time 2026-01-27T23:59:59Z \
  --period 3600 \
  --statistics Sum
```

---

## Summary

**Notification Configuration Checklist**:

1. ✅ Configure delivery channels (email, SMS, push, in-app)
2. ✅ Set up AWS services (SES, SNS)
3. ✅ Create and upload message templates
4. ✅ Configure opt-out management
5. ✅ Set up frequency capping rules
6. ✅ Enable delivery tracking
7. ✅ Test all channels and templates
8. ✅ Monitor delivery metrics and engagement

**Your notification system is now configured!**

---

## Next Steps

- **Test Delivery**: Send test messages through all channels
- **Monitor Engagement**: Track open rates, click rates, and conversions
- **Optimize Templates**: A/B test subject lines and content
- **Review Compliance**: Ensure GDPR, CAN-SPAM, TCPA compliance
- **Scale Gradually**: Start with small volumes and increase

---

**Need help?** Check `docs/TROUBLESHOOTING.md` or review `docs/USE-CASES.md` for complete examples.
