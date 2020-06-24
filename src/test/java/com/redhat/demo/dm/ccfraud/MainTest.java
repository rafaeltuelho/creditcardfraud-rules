package com.redhat.demo.dm.ccfraud;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.definition.type.Timestamp;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionClock;
import org.kie.api.time.SessionPseudoClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.demo.dm.ccfraud.domain.CountryCode;
import com.redhat.demo.dm.ccfraud.domain.CreditCardTransaction;
import com.redhat.demo.dm.ccfraud.domain.Terminal;

/**
 * Test Scenario for the demo project wich creates a new {@link CreditCardTransaction}, 
 * loads the previous transactions from a CSV file and uses
 * the Drools CEP engine to determine whether there was a potential fraud with the transactions.
 * 
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 */
public class MainTest {

	private final Logger LOGGER = LoggerFactory.getLogger(MainTest.class);
	private final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd:HHmmssSSS");
	private final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss:SSS", Locale.US);
	private final KieServices KIE_SERVICES = KieServices.Factory.get();
	private KieContainer kieContainer;

	private CreditCardTransactionRepository cctRepository = new InMemoryCreditCardTransactionRepository();

	@Test
	public void mainTest() {
		// Load the Drools KIE-Container.
		kieContainer = KIE_SERVICES.getKieClasspathContainer();

		long transactionTime = 0L;
		try {
			transactionTime = DATE_FORMAT.parse("20180629:094000000").getTime();
		} catch (ParseException pe) {
			throw new RuntimeException(pe);
		}

		// Define the new incoming credit-card transaction. In an actual system, this event would come a Kafka stream or a Vert.x EventBus
		// event.
		CreditCardTransaction incomingTransaction = new CreditCardTransaction(
			100, 12345, new BigDecimal(10.99), transactionTime, new Terminal(1, CountryCode.US));

		// Process the incoming transaction.
		processTransaction(incomingTransaction);
	}

	private void processTransaction(CreditCardTransaction ccTransaction) {
		// Retrieve all transactions for this account
		Collection<CreditCardTransaction> ccTransactions = cctRepository
				.getCreditCardTransactionsForCC(ccTransaction.getCreditCardNumber());

		LOGGER.debug("Found '" + ccTransactions.size() + "' transactions for creditcard: '" + ccTransaction.getCreditCardNumber() + "'.");

		KieSession kieSession = kieContainer.newKieSession("cdfd-session");
		// Insert transaction history/context.
		for (CreditCardTransaction nextTransaction : ccTransactions) {
			insert(kieSession, "Transactions", nextTransaction);
		}
		// Insert the new transaction event
		LOGGER.debug(" ");
		LOGGER.debug("Inserting credit-card transaction event into session.");
		insert(kieSession, "Transactions", ccTransaction);
		// And fire the rules.
		kieSession.fireAllRules();

		// Dispose the session to free up the resources.
		kieSession.dispose();

	}

	/**
	 * CEP insert method that inserts the event into the Drools CEP session and programatically advances the session clock to the time of
	 * the current event.
	 * 
	 * @param kieSession
	 *            the session in which to insert the event.
	 * @param stream
	 *            the name of the Drools entry-point in which to insert the event.
	 * @param cct
	 *            the event to insert.
	 * 
	 * @return the {@link FactHandle} of the inserted fact.
	 */
	private FactHandle insert(KieSession kieSession, String stream, CreditCardTransaction cct) {
		SessionClock clock = kieSession.getSessionClock();
		if (!(clock instanceof SessionPseudoClock)) {
			String errorMessage = "This fact inserter can only be used with KieSessions that use a SessionPseudoClock";
			LOGGER.error(errorMessage);
			throw new IllegalStateException(errorMessage);
		}
		SessionPseudoClock pseudoClock = (SessionPseudoClock) clock;
		LOGGER.debug( "\tCEP Engine PseudoClock current time: " + LocalDateTime.ofInstant(Instant.ofEpochMilli(pseudoClock.getCurrentTime()), ZoneId.systemDefault()).toString() );
		EntryPoint ep = kieSession.getEntryPoint(stream);

		// First insert the event
		FactHandle factHandle = ep.insert(cct);
		// And then advance the clock.
		LOGGER.debug(" ");
		LOGGER.debug("Inserting credit-card [" + cct.getCreditCardNumber() + "] transaction [" + cct.getTransactionNumber() + "] context into session.");
		String dateTimeFormatted = LocalDateTime.ofInstant(
			Instant.ofEpochMilli(cct.getTimestamp()), ZoneId.systemDefault()).format(DATE_TIME_FORMAT);
		LOGGER.debug( "\tCC Transaction Time: " + dateTimeFormatted);
		long advanceTime = cct.getTimestamp() - pseudoClock.getCurrentTime();
		if (advanceTime > 0) {
			long tSec = advanceTime/1000;
			LOGGER.debug("\tAdvancing the PseudoClock with " + advanceTime + " milliseconds (" + tSec + "sec)" );
			
			pseudoClock.advanceTime(advanceTime, TimeUnit.MILLISECONDS);
			dateTimeFormatted = LocalDateTime.ofInstant(
				Instant.ofEpochMilli(pseudoClock.getCurrentTime()), ZoneId.systemDefault()).format(DATE_TIME_FORMAT);
			LOGGER.debug( "\tCEP Engine PseudoClock ajusted time: " +  dateTimeFormatted);
		} else {
			// Print a warning when we don't need to advance the clock. This usually means that the events are entering the system in the
			// incorrect order.
			LOGGER.warn("Not advancing time. CreditCardTransaction timestamp is '" + cct.getTimestamp() + "', PseudoClock timestamp is '"
					+ pseudoClock.getCurrentTime() + "'.");
		}
		return factHandle;
	}
}
