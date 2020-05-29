import time
import traceback

import geniusweb.party.inform.ActionDone as ActionDone
import geniusweb.party.inform.Inform as Inform
import geniusweb.party.inform.Settings as Settings
import geniusweb.party.inform.YourTurn as YourTurn


import java.math.BigInteger as BigInteger
import java.net.URI as URI
import java.util.Arrays as Arrays
import java.util.HashSet as HashSet
import java.util.Random as Random
 
import geniusweb.actions.Accept as Accept
import geniusweb.actions.Action as Action
import geniusweb.actions.Offer as Offer
import geniusweb.actions.PartyId as PartyId
import geniusweb.bidspace.AllPartialBidsList as AllPartialBidsList
import geniusweb.issuevalue.Bid as Bid
import geniusweb.party.Capabilities as Capabilities
import geniusweb.party.DefaultParty as DefaultParty
import geniusweb.profile.Profile as Profile
import geniusweb.profile.PartialOrdering as PartialOrdering
import geniusweb.profile.utilityspace.UtilitySpace as UtilitySpace
import geniusweb.references.ProfileRef as ProfileRef
import geniusweb.references.ProtocolRef as ProtocolRef
import geniusweb.profileconnection.ProfileConnectionFactory as ProfileConnectionFactory
import geniusweb.profileconnection.ProfileInterface as ProfileInterface
import geniusweb.progress.ProgressRounds as ProgressRounds


import com.fasterxml.jackson.databind.ObjectMapper as ObjectMapper

class RandomParty (DefaultParty):
	"""
	A simple party that places random bids and accepts when it receives an offer
	with sufficient utility.
	"""

	jackson = ObjectMapper()
	random = Random();

	def __init__(self):
		self.profile = None
		self.lastReceivedBid = None

	# Override
	def notifyChange(self, info):
		if isinstance(info, Settings) :
			self.profile = ProfileConnectionFactory.create(info.getProfile().getURI(), self.getReporter());
			self.me = info.getID()
			self.progress = info.getProgress()
		elif isinstance(info , ActionDone): 
			self.lastActor = info.getAction().getActor()
			otheract = info.getAction()
			if isinstance(otheract, Offer):
				self.lastReceivedBid = otheract.getBid()
		elif isinstance(info , YourTurn):
			self._myTurn()
			if isinstance(self.progress, ProgressRounds) :
				self.progress = self.progress.advance();

	# Override
	def getCapabilities(self): # -> Capabilities
			return Capabilities(HashSet([ "SAOP"]))

	# Override
	def getDescription(self):
		return "places random bids until it can accept an offer with utility >0.6. Python version"

	def _myTurn(self):
		if self.lastReceivedBid != None and self.profile.getProfile().getUtility(self.lastReceivedBid).doubleValue() > 0.6:
			action = Accept(self.me, self.lastReceivedBid)
		else:
			bidspace = AllPartialBidsList(self.profile.getProfile().getDomain())
			bid = None
			for attempt in range(20):
				i = self.random.nextInt(bidspace.size()) # warning: jython implicitly converts BigInteger to long.
				bid = bidspace.get(BigInteger.valueOf(i))
				if self._isGood(bid):
					break
			action = Offer(self.me, bid);
		try:
			self.getConnection().send(action)
		except:
			print 'failed to send action '+action.toString()
			traceback.print_exc()

	def _isGood(self, bid):
		if bid == None:
			return false
		profile = self.profile.getProfile()
		if isinstance(profile, UtilitySpace):
			return profile.getUtility(bid).doubleValue() > 0.6;
		if isinstance(profile, PartialOrdering):
			return profile.isPreferredOrEqual(bid, profile.getReservationBid())
		raise Exception("Can not handle this type of profile")
