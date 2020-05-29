package geniusweb.exampleparties.simpleshaop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.profile.DefaultPartialOrdering;

public class SimpleLinearOrderingTest {
	private DefaultPartialOrdering realprofile = mock(
			DefaultPartialOrdering.class);;
	private Domain domain = mock(Domain.class);
	private SimpleLinearOrdering estprofile;
	private Bid bid1 = makeBid(1);
	private Bid bid2 = makeBid(2);
	private Bid bid3 = makeBid(3);
	private Bid bid4 = makeBid(4);
	private final List<Bid> emptyList = Collections.emptyList();

	@Before
	public void before() {
		when(realprofile.getDomain()).thenReturn(domain);
		estprofile = new SimpleLinearOrdering(realprofile);
		when(bid1.toString()).thenReturn("bid1");

	}

	private static Bid makeBid(int n) {
		Bid mockbid = mock(Bid.class);
		when(mockbid.toString()).thenReturn("bid " + n);
		return mockbid;
	}

	@Test
	public void testSmoke() {
	}

	@Test
	public void testInitialBids() {
		assertTrue(estprofile.getBids().isEmpty());
	}

	@Test
	public void largerInitialListTest() {
		when(realprofile.getBids()).thenReturn(Arrays.asList(bid1, bid2, bid3));
		// idea is that 3 > 2 > 1
		when(realprofile.isPreferredOrEqual(bid2, bid1)).thenReturn(true);
		when(realprofile.isPreferredOrEqual(bid1, bid2)).thenReturn(false);
		when(realprofile.isPreferredOrEqual(bid3, bid2)).thenReturn(true);
		when(realprofile.isPreferredOrEqual(bid2, bid3)).thenReturn(false);
		when(realprofile.isPreferredOrEqual(bid3, bid1)).thenReturn(true);
		when(realprofile.isPreferredOrEqual(bid1, bid3)).thenReturn(false);
		SimpleLinearOrdering testprofile = new SimpleLinearOrdering(
				realprofile);
		assertEquals(Arrays.asList(bid1, bid2, bid3), testprofile.getBids());
	}

	@Test
	public void testAddFirstBid() {
		SimpleLinearOrdering prof1 = estprofile.with(bid1,
				Collections.emptyList());

		assertEquals(1, prof1.getBids().size());
	}

	@Test
	public void testAddTwoBidsSecondIsWorse() {
		SimpleLinearOrdering prof1 = estprofile.with(bid1, emptyList);
		// new bid is worse as bid1, so list of worse bids is empty
		SimpleLinearOrdering prof2 = prof1.with(bid2, emptyList);

		assertEquals(Arrays.asList(bid2, bid1), prof2.getBids());
	}

	@Test
	public void testAddTwoBidsSecondIsBetter() {
		SimpleLinearOrdering prof1 = estprofile.with(bid1, emptyList);
		SimpleLinearOrdering prof2 = prof1.with(bid2, Arrays.asList(bid1));

		assertEquals(Arrays.asList(bid1, bid2), prof2.getBids());
	}

	@Test
	public void testAddThreeBidsThirdIsMiddle() {
		SimpleLinearOrdering prof1 = new SimpleLinearOrdering(domain,
				Arrays.asList(bid1, bid2));
		SimpleLinearOrdering prof2 = prof1.with(bid3, Arrays.asList(bid1));

		assertEquals(Arrays.asList(bid1, bid3, bid2), prof2.getBids());
	}

	@Test
	public void getUtilityTest3Bids() {
		SimpleLinearOrdering prof1 = new SimpleLinearOrdering(domain,
				Arrays.asList(bid1, bid3, bid2));

		assertEquals(new BigDecimal("0.50000000"), prof1.getUtility(bid3));
	}

	@Test
	public void getUtilityTest4Bids() {
		// with 4 bids, we get utility 1/3 which causes deximal expansiion issue
		SimpleLinearOrdering prof1 = new SimpleLinearOrdering(domain,
				Arrays.asList(bid1, bid2, bid3, bid4));

		assertEquals(new BigDecimal("0.33333333"), prof1.getUtility(bid2));
	}

}
