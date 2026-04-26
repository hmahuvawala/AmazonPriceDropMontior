# AI Notes

During this build, my AI assistant helped move quickly, but it also got several important things wrong or oversimplified parts of the system.

## Spring Boot Lifecycle Bug

The first issue was a Spring Boot lifecycle bug in the email notification layer. The AI generated an `EmailNotifier` class with `@Component` and `@ConditionalOnBean(JavaMailSender.class)`, assuming this would only enable email when Spring Mail was configured.

In practice, the email notifier was never registered. The console notifier worked, but emails never arrived. I caught this by triggering a fake notification with `curl` and then debugging the Spring bean initialization path. The issue was timing: during component scanning, `JavaMailSender` from auto-configuration was not visible yet, so the condition evaluated false too early. I fixed this by removing the faulty conditional annotation and managing the email notifier registration more safely.

I also had to discover and configure a Gmail App Password manually, which the AI did not mention.

## Architectural Oversimplification

The second issue was architectural oversimplification. The AI originally modeled price-drop alerts around percentage thresholds only, even though the project needed absolute dollar-value drops as well. It also treated product notification settings as mostly fixed after creation.

I corrected this by adding database migrations to support mutable product configurations and absolute-value thresholds. The AI also hardwired notification delivery to a single Slack webhook implementation instead of preserving a strategy-style design. I refactored this into a `NotificationChannel` interface so the core price-checking logic was decoupled from the delivery mechanism.

## Setup Time Trap

The third issue was real-world setup friction. The AI initially recommended Slack webhooks because the implementation looked simple in code. What it missed was the setup cost: Slack workspace permissions, app configuration, OAuth, and redirect URL setup became a major time sink.

I decided to pivot to SMTP email and Twilio SMS instead. That introduced its own setup and validation issues, including Twilio free-tier limitations, but it was more practical for the timebox. I logged the Twilio output safely, documented the known issue, and stopped development instead of burning more time chasing external API credentials.

Overall, the AI was useful for generating structure quickly, but it tended to optimize for the easiest code path rather than the full product requirements or real deployment friction. I had to validate the generated code with manual tests, debugger traces, database changes, and architectural refactoring before the system matched the intended behavior.

## SMS Messaging via Twilio

Twilio was an easy way to setup SMS Messaging for the time frame of 2 to 4 hours allocated. The API works, but there was an internal problem with Twilio that did not allow messages to get sent. I identified this issue, but due to time constraints had to move on. For the Twilio API to work, I would need to verify my account, for which I would need to enter business details which I do not have for this project. 
