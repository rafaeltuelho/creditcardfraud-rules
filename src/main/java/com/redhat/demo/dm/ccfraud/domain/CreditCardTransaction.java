package com.redhat.demo.dm.ccfraud.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Represents a credit-card transaction.
 * <p/>
 * This class is immutable.
 * 
 * @author <a href="mailto:ddoyle@redhat.com">Duncan Doyle</a>
 */
public class CreditCardTransaction {

	private long transactionNumber;
	private long creditCardNumber;
	private BigDecimal amount;
	private long timestamp;
	private Terminal terminal;
	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss:SSS", Locale.US);

	public CreditCardTransaction(long transactionNumber, long creditCardNumber, BigDecimal amount, long timestamp, Terminal terminal) {
		this.transactionNumber = transactionNumber;
		this.creditCardNumber = creditCardNumber;
		this.amount = amount;
		this.timestamp = timestamp;
		this.terminal = terminal;
	}

	public long getTransactionNumber() {
		return transactionNumber;
	}
	
	public long getCreditCardNumber() {
		return creditCardNumber;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public Terminal getTerminal() {
		return terminal;
	}

	@Override
	public String toString() {
		return "{" +
			" transactionNumber='" + getTransactionNumber() + "'" +
			", creditCardNumber='" + getCreditCardNumber() + "'" +
			", amount='" + getAmount() + "'" +
			", timestamp='" + 
				LocalDateTime.ofInstant(Instant.ofEpochMilli(getTimestamp()), ZoneId.systemDefault()).format(DATE_TIME_FORMAT) + 
				"'" +
			", terminal='" + getTerminal() + "'" +
			"}";
	}
	
}
