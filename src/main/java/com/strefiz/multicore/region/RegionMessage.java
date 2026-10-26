package com.strefiz.multicore.region;

import java.util.Objects;

/**
 * A message sent from one region to another, processed by the target region's owner thread at the
 * end of the tick.
 *
 * <p>Cross-region interactions (teleports, redstone, explosions spanning boundaries, entity movement
 * across region edges) must go through messages, never through direct access to another region's
 * state. This is Folia's inter-region operation model.
 *
 * @param <T> the payload type
 */
public final class RegionMessage<T> {

	private final RegionSection sourceSection;
	private final RegionSection targetSection;
	private final T payload;
	private final Runnable onDeliver;

	/**
	 * Creates a cross-region message.
	 *
	 * @param sourceSection the section sending the message
	 * @param targetSection the section receiving the message
	 * @param payload the message payload
	 * @param onDeliver optional action to run when the message is delivered on the target thread
	 */
	public RegionMessage(RegionSection sourceSection, RegionSection targetSection, T payload,
			Runnable onDeliver) {
		this.sourceSection = sourceSection; // may be null for same-region delivery
		this.targetSection = targetSection; // may be null for same-region delivery
		this.payload = Objects.requireNonNull(payload, "payload");
		this.onDeliver = onDeliver;
	}

	/** @return the section sending the message. */
	public RegionSection sourceSection() {
		return sourceSection;
	}

	/** @return the section receiving the message. */
	public RegionSection targetSection() {
		return targetSection;
	}

	/** @return the message payload. */
	public T payload() {
		return payload;
	}

	/** Runs the delivery action on the target thread. */
	public void deliver() {
		if (onDeliver != null) {
			onDeliver.run();
		}
	}

	@Override
	public String toString() {
		return "RegionMessage(" + sourceSection + " -> " + targetSection + ")";
	}
}
