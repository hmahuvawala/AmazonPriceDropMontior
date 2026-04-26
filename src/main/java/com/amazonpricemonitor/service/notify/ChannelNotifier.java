package com.amazonpricemonitor.service.notify;

/**
 * Marks a concrete notification channel bean (log, email, SMS). {@link CompositeNotifier}
 * implements {@link Notifier} but not this type so Spring can inject {@code List<ChannelNotifier>}
 * without including the composite itself.
 */
public interface ChannelNotifier extends Notifier {}
