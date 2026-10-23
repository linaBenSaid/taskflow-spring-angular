package com.taskflow.util;

import java.util.Locale;

public final class Emails {

	private Emails() {
	}

	// "Alice@Mail.com " and "alice@mail.com" are the same account.
	public static String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
