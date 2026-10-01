package com.keni.starter.modules.notifications;

import com.keni.starter.modules.userSubscriptions.UserSubscription;

/**
 * Sends a message to a user.
 *
 * <p>An interface rather than a direct JavaMailSender call so the reminder job can be
 * tested without an SMTP server. The application ships a logging implementation; a real
 * deployment supplies its own bean and nothing else changes.
 */
public interface ExpiryNotifier {

  /**
   * Tells the holder that their subscription is about to run out.
   *
   * <p>Called at most once per subscription per expiry, guarded by
   * {@code reminder_sent_at}, so this does not need its own de-duplication.
   */
  void sendExpiryReminder(UserSubscription userSubscription, int daysRemaining);
}