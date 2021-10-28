package group33;

import genius.core.AgentID;
import genius.core.Bid;
import genius.core.actions.Accept;
import genius.core.actions.Action;
import genius.core.actions.Offer;
import genius.core.issue.*;
import genius.core.parties.AbstractNegotiationParty;
import genius.core.parties.NegotiationInfo;
import genius.core.uncertainty.AdditiveUtilitySpaceFactory;
import genius.core.uncertainty.BidRanking;
import genius.core.uncertainty.OutcomeComparison;
import genius.core.utility.AbstractUtilitySpace;
import genius.core.utility.AdditiveUtilitySpace;
import genius.core.utility.Evaluator;
import genius.core.utility.EvaluatorDiscrete;
import gurobi.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentHSortaPref2 extends AbstractNegotiationParty {
    private final double compUtilityThreshold = 0.6;
    private final double initialThreshold = 0.8;
    private ArrayList<Bid> receivedBids;
    private HashMap<Issue,Value> bestValues;
    private Bid lastReceivedOffer;
    private Bid lastSentOffer;
    private List<Bid> bids;

    @Override
    public Action chooseAction(List<Class<? extends Action>> possibleActions) {
        if (lastReceivedOffer != null){
            if ((this.utilitySpace.getUtility(lastReceivedOffer) > this.utilitySpace.getReservationValue())&& (this.getTimeLine().getCurrentTime() == this.getDeadlines().getValue() -1)){  //accept if on the second to last round and offer is higher than reservation value
                System.out.println("accepting as second to last round and offer better than reservation value");
                return new Accept(this.getPartyId(), lastReceivedOffer);
            }
            if (lastSentOffer != null && (this.utilitySpace.getUtility(lastReceivedOffer) >= this.utilitySpace.getUtility(lastSentOffer))){   //if the offer has a higher or same utility than the last one sent
                System.out.println("accepting as offer has a higher or same utility than last offer we sent");
                return new Accept(this.getPartyId(), lastReceivedOffer);
            } else
            if (calcCompThreshold(lastReceivedOffer)) {     //if the offer has a higher compromising utility than the threshold
                System.out.println("accepting as offer has a higher compromising utility than threshold of "+compUtilityThreshold);
                return new Accept(this.getPartyId(), lastReceivedOffer);
            } //send new bid
            System.out.println("attempting to send a bid");
            for (Bid bid : bids){
                if (receivedBids.contains(bid) && bid != null){ //find highest utility bid in ranking that we have previously received (ignore null bids if they slipped in somehow)
                    Bid offeredBid = raiseUtility(bid);
                    lastSentOffer = offeredBid;
                    return new Offer(this.getPartyId(),offeredBid);
                }
            }
        } else {
            return new Offer(this.getPartyId(),this.userModel.getBidRanking().getMaximalBid());
        }
        System.out.println("returning null, you messed up");
        return null;
    }

    private Bid raiseUtility(Bid bid){ //alter one of the issues in the bid in a way that raises the utility of the bid.
        System.out.println("bid to be raised: "+ bid);
        double currentUtility = this.utilitySpace.getUtility(bid);
        System.out.println("utility to beat: "+currentUtility);
        int index = 0;
        do {
            Issue i = bid.getIssues().get(index);
            Value v = null;
            try {
                v = bestValues.get(i);
            } catch (Exception e){
                System.out.println("issue: "+i);
            }
            System.out.println("modifying "+ bid);
            bid = bid.putValue(i.getNumber(),v);
            System.out.println("new bid "+bid);
            System.out.println("new utility "+this.utilitySpace.getUtility(bid));
            index++;
            System.out.println(evaluateBid(bid,currentUtility,index));
        } while (evaluateBid(bid,currentUtility,index));
        return bid;
    }

    private boolean evaluateBid(Bid bid,double currentUtility, int index){   //return true for loop to continue if bid unacceptable
        if (index >= bid.getIssues().size()){
            System.out.println("all issues entered, sending max value");
            return false;
        }
        if (this.utilitySpace.getUtility(bid) <= currentUtility){
            return true;
        }
        if (this.utilitySpace.getUtility(bid) > initialThreshold){
            System.out.println("offer generated above initial threshold - sending");
            return false;
        }
        if (calcCompThreshold(bid)){
            System.out.println("bid greater than compromising threshold - sending");
            return false;
        }
        if (index >= bid.getIssues().size()){
            System.out.println("all issues entered, sending max value");
            return false;
        }
        return true;
    }

    private boolean calcCompThreshold(Bid b){
        double u = this.utilitySpace.getUtility(b);
        double t = getTimeLine().getTime();
        return (u > (-t+(1+compUtilityThreshold)));
    }

    @Override
    public void receiveMessage(AgentID sender, Action act) {
        super.receiveMessage(sender, act);

        if (act instanceof Offer) { // sender is making an offer
            Offer offer = (Offer) act;
            Bid bid = offer.getBid();
            receivedBids.add(bid);
            // storing last received offer
            lastReceivedOffer = bid;
        }
    }

    public void init(NegotiationInfo info){
        super.init(info);
        receivedBids = new ArrayList<>();
        bestValues = new HashMap<>();
        bids = getAllPossibleBids();
        AdditiveUtilitySpaceFactory additiveUtilitySpaceFactory = new AdditiveUtilitySpaceFactory(getDomain());
        List<IssueDiscrete> issues = additiveUtilitySpaceFactory.getIssues();
        userModel = info.getUserModel();
        utilitySpace = estimateUtilitySpace();
        userModel = SDUserPref.AttemptBother(issues, info.getUser(), userModel, utilitySpace);
        utilitySpace = estimateUtilitySpace();
        sortBids();
        user = info.getUser();
        try {
            Bid maxBid = utilitySpace.getMaxUtilityBid();
            for (Issue i : maxBid.getIssues()){
                bestValues.put(i,maxBid.getValue(i));
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private List<Bid> getAllPossibleBids(){
        List<Issue> issues = getDomain().getIssues();
        return generateBids(issues, new int[0]);
    }

    private List<Bid> generateBids(List<Issue> issues, int[] selectedOptions){
        List<Bid> bids = new ArrayList<>();
        if (selectedOptions.length == issues.size()){
            HashMap<Integer, Value> issueValues = new HashMap<>();
            for (int i=0;i<issues.size();i++){
                IssueDiscrete issueDiscrete = (IssueDiscrete) issues.get(i);
                issueValues.put(i+1, issueDiscrete.getValue(selectedOptions[i]));
            }
            bids.add(new Bid(getDomain(), issueValues));
            return bids;
        }
        IssueDiscrete nextIssue = (IssueDiscrete)issues.get(selectedOptions.length);
        int[] newSelections = new int[selectedOptions.length + 1];
        System.arraycopy(selectedOptions, 0, newSelections, 0, selectedOptions.length);
        for(int j=0;j<nextIssue.getNumberOfValues();j++){
            newSelections[newSelections.length - 1] = j;
            bids.addAll(generateBids(issues, newSelections));
        }
        return bids;
    }

    private void sortBids(){
        bids.sort((o1, o2) -> {
            double u1 = utilitySpace.getUtility(o1);
            double u2 = utilitySpace.getUtility(o2);
            double diff = u1 - u2;
            int out = diff < 0 ? 1 : (diff > 0 ? -1 : 0);
            return out;
        });
    }

    private double getCompUtility(Bid bid){
        return this.utilitySpace.getUtility(bid) * getTimeLine().getTime();    //getTime is normalised time between 0 and 1
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public AbstractUtilitySpace estimateUtilitySpace()
    {
        try {
            return estimateUtilitySpaceFromBidRanking(userModel.getBidRanking());
        } catch (GRBException ex){
            ex.printStackTrace();
            return super.estimateUtilitySpace();
        }
    }

    // Strategy from:
    // Automated Negotiations under User Preference Uncertainty: A Linear Programming Approach (D. Tsimpoukis et al.)
    // https://www.researchgate.net/profile/Tim_Baarslag/publication/332191284_Automated_Negotiations_Under_User_Preference_Uncertainty_A_Linear_Programming_Approach/links/5cadc27a299bf193bc2dc3da/Automated-Negotiations-Under-User-Preference-Uncertainty-A-Linear-Programming-Approach.pdf?origin=publication_detail

    private AdditiveUtilitySpace estimateUtilitySpaceFromBidRanking(BidRanking bidRanking) throws GRBException {
        // Create objects to hold utility space information
        Map<Objective, Evaluator> fEvaluators = new HashMap<>();
        List<Issue> issues = getDomain().getIssues();
        List<Bid> bidOrder = bidRanking.getBidOrder();
        List<OutcomeComparison> D = bidRanking.getPairwiseComparisons();
        Bid best = bidRanking.getMaximalBid();

        // Setup solver environment
        GRBEnv env = new GRBEnv(true);
        env.set(GRB.IntParam.LogToConsole, 0);
        env.start();
        GRBModel model = new GRBModel(env);

        // Create variables
        // z is the set of slack variables, one for each outcome comparison
        HashMap<OutcomeComparison, GRBVar> z = new HashMap<>();

        // F is the sum of all z(o,o') in z
        GRBLinExpr F = new GRBLinExpr();
        for (OutcomeComparison comp : D){
            int o1 = bidOrder.indexOf(comp.getBid1());
            int o2 = bidOrder.indexOf(comp.getBid1());
            GRBVar zoo = model.addVar(0, 1, 0, GRB.CONTINUOUS, "z" + o1 + o2);
            z.put(comp, zoo);
            F.addTerm(1, zoo);
        }

        // Objective: Minimise F
        model.setObjective(F, GRB.MINIMIZE);

        // Y is the set of weighted evaluation functions for all options for all issues
        // Y[i][j] is the weighted evaluation of value j for issue i
        GRBVar[][] Y = new GRBVar[issues.size()][];
        for (Issue issue : issues){
            IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
            Y[issue.getNumber() - 1] = new GRBVar[issueDiscrete.getNumberOfValues()];
            List<ValueDiscrete> values = issueDiscrete.getValues();
            for (ValueDiscrete value : values){
                int i = issue.getNumber() - 1;
                int j = issueDiscrete.getValueIndex(value);
                Y[i][j] = model.addVar(0, 1, 0, GRB.CONTINUOUS, "u" + i + j);
            }
        }

        // Iterate through order comparisons to define constraints:
        //   z(o, o') + uDiff(o, o') >= 0 for all (o, o') in D
        //   z(o, o') >= 0 for all (o, o') in D
        int count = 0;
        for (OutcomeComparison comp : D){
            Bid o1 = comp.getBid1();
            Bid o2 = comp.getBid2();
            GRBLinExpr expr = new GRBLinExpr();
            expr.addTerm(1, z.get(comp));
            GRBLinExpr uDiff = new GRBLinExpr();
            for (Issue issue : issues){
                IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
                int i = issue.getNumber() - 1;
                GRBVar val1 = Y[i][(issueDiscrete.getValueIndex((ValueDiscrete) o1.getValue(issue)))];
                GRBVar val2 = Y[i][(issueDiscrete.getValueIndex((ValueDiscrete) o2.getValue(issue)))];
                GRBLinExpr uDiffI = new GRBLinExpr();
                uDiffI.addTerm(-1, val1);
                uDiffI.addTerm(1, val2);
                uDiff.add(uDiffI);
            }
            expr.add(uDiff);
            model.addConstr(expr, GRB.GREATER_EQUAL, 0, "c"+count);
            count++;
        }
        GRBLinExpr maxBidUtility = new GRBLinExpr();
        // Maximal bid constraint: Best bid has utility 1
        for(Issue issue : issues){
            int i = issue.getNumber() - 1;
            IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
            int j = issueDiscrete.getValueIndex((ValueDiscrete) best.getValue(issue));
            GRBVar var = Y[i][j];
            maxBidUtility.addTerm(1, var);
        }
        model.addConstr(maxBidUtility, GRB.EQUAL, 1, "maxBid");
        model.optimize();
        // Extract utility space information from optimised model
        for (int i=0;i<issues.size();i++){
            Issue issue = issues.get(i);
            IssueDiscrete issueDiscrete = (IssueDiscrete) issue;
            List<ValueDiscrete> values = issueDiscrete.getValues();

            HashMap<ValueDiscrete, Double> fEval = new HashMap<>();

            // Determine issue weight from max option value
            double weight = 0;
            for (int j=0;j<values.size();j++){
                double val = Y[i][j].get(GRB.DoubleAttr.X);
                weight = Math.max(weight, val);
            }

            // Place option utilities into mapping, adjusted according to weight
            for (int j=0;j<values.size();j++){
                double val = 0;
                if (weight > 0) {
                    val = Y[i][j].get(GRB.DoubleAttr.X) / weight;
                }
                fEval.put(values.get(j), val);
            }
            Evaluator evaluator = new EvaluatorDiscrete(fEval);
            evaluator.setWeight(weight);
            fEvaluators.put(issue, evaluator);
        }

        AdditiveUtilitySpace utilitySpace = new AdditiveUtilitySpace(getDomain(), fEvaluators);
        model.dispose();
        env.dispose();
        return utilitySpace;
    }
}