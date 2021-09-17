// A satisficing agent based off multi-objective
// Q-learning for the AI safety side-effects research project. The agent expects a
// vector of 3 values - the (incorrectly specified) goal reward, our potential-based
// impact reward, and the true performance reward. It ignores the latter (which it 
// shouldn't have access to). Unlike the safety first agent, it first focuses on achieving
// a threshold level of performance for the primary reward, then on minimising the impact penalty, and
// then (as a tie-breaker) maximising the primary reward. This should make it more robust to exploratory actions during learning. 
// However it means that the state-space needs to be augmented with the primary reward received so far, which will
// probably result in slower learning.

package agents;

import org.rlcommunity.rlglue.codec.AgentInterface;
import org.rlcommunity.rlglue.codec.RLGlue;
import org.rlcommunity.rlglue.codec.taskspec.TaskSpecVRLGLUE3;
import org.rlcommunity.rlglue.codec.types.Action;
import org.rlcommunity.rlglue.codec.types.Observation;
import org.rlcommunity.rlglue.codec.types.Reward;
import org.rlcommunity.rlglue.codec.util.AgentLoader;
import tools.staterep.DummyStateConverter;
import tools.staterep.interfaces.StateConverter;
import tools.traces.StateActionIndexPair;
import tools.valuefunction.SatisficingLookupTable;
import tools.valuefunction.SatisficingMILookupTable;
import tools.valuefunction.TLO_LookupTable;
import tools.valuefunction.interfaces.ActionSelector;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.Stack;


public class SatisficingMOMIAgent implements AgentInterface {

    boolean isApologetic = false; // set by message call
    boolean conscienceInit = false;
    boolean[] apologisedFor;
    Conscience myConscience;

	// Problem-specific parameters - at some point I need to refactor the code in such a way that these can be set externally
     int thresholdIndex = 0;  // provide the threshold to initialise at

    // Threshold adjustment details
    int [] thresholdMinimum = {-1000, -50, -50}; // The minimum value that each threshold can take
    int [] thresholdMaximum = {35, 0, 0}; // The maximum value that each threshold can take
//    int alr = 10; // apologetic learning rate
//    double [] delta = {((double)(thresholdMaximum[0]-thresholdMinimum[0]))/alr,
//            ((double)(thresholdMaximum[1]-thresholdMinimum[1]))/alr,
//            ((double)(thresholdMaximum[2]-thresholdMinimum[2]))/alr}; // The learning rate of the apology framework
    double [] delta = {103.5, 5, 5}; //explicitly calculated

    double [][] allThresholds = {
             {thresholdMaximum[0], thresholdMaximum[1], thresholdMaximum[2]}, //{0, 0, 0},
             {thresholdMaximum[0], thresholdMaximum[1], thresholdMinimum[2]}, //{0, 0, -50},
             {thresholdMaximum[0], thresholdMinimum[1], thresholdMaximum[2]}, //{0, -50, 0},
             {thresholdMaximum[0], thresholdMinimum[1], thresholdMinimum[2]}, //{0, -50, -50},
             {thresholdMinimum[0], thresholdMaximum[1], thresholdMaximum[2]}, //{-50, 0, 0},
             {thresholdMinimum[0], thresholdMaximum[1], thresholdMinimum[2]}, //{-50, 0, -50},
             {thresholdMinimum[0], thresholdMinimum[1], thresholdMaximum[2]}, //{-50, -50, 0},
             {thresholdMinimum[0], thresholdMinimum[1], thresholdMinimum[2]}, //{-50, -50, -50}
    };

    double [] currentThresholds;





//    double primaryRewardThreshold = 0; // sets threshold on the acceptable minimum level of performance on the primary reward // use high value here to get lex-pa
//    double impactThreshold1 = 0; //-0.1; //use high value if you want to 'switch off' thresholding (ie to get TLO-P rather than TLO-PA)
//    double impactThreshold2 = -50; //-0.1; //use high value if you want to 'switch off' thresholding (ie to get TLO-P rather than TLO-PA)

//    double primaryRewardThreshold = thresholds[0]; // sets threshold on the acceptable minimum level of performance on the primary reward // use high value here to get lex-pa
//    double impactThreshold1 = thresholds[1]; //-0.1; //use high value if you want to 'switch off' thresholding (ie to get TLO-P rather than TLO-PA)
//    double impactThreshold2 = thresholds[2]; //-0.1; //use high value if you want to 'switch off' thresholding (ie to get TLO-P rather than TLO-PA)

    double primaryRewardThreshold = allThresholds[thresholdIndex][0]; // sets threshold on the acceptable minimum level of performance on the primary reward // use high value here to get lex-pa
    double impactThreshold1 = allThresholds[thresholdIndex][1]; //-0.1; //use high value if you want to 'switch off' thresholding (ie to get TLO-P rather than TLO-PA)
    double impactThreshold2 = allThresholds[thresholdIndex][2]; //-0.1; //use high value if you want to 'switch off' thresholding (ie to get TLO-P rather than TLO-PA)

    double minPrimaryReward = -1000; // the lowest reward obtainable
    double maxPrimaryReward = 50;	// the highest reward obtainable

  
    int numDiscretisationsOfReward = 10; //10; // how many divisions in the discretisation of the accumulated reward?
    double discretisationGranularity = 0.001 + (maxPrimaryReward - minPrimaryReward)/(numDiscretisationsOfReward); // how big is each cell in the discretisation of the accumulated reward? Add 0.001 to avoid rounding up the max value to be out of the index range
    
	SatisficingMILookupTable vf = null;
    Stack<StateActionIndexPair> tracingStack = null;

    private boolean policyFrozen = false;
    private boolean debugging = false;
    private Random random;

    private int numActions = 0;
    private int numEnvtStates = 0; // number of states in the environment
    private int numStates; // number of augmented states (agent state = environmental-state U accumulated-primary-reward)
    int numOfObjectives;

    private final double initQValues[]={0,0,0,0};  //was {0,0,0}
    int explorationStrategy; // flag used to indicate which type of exploration strategy is being used
    //if using eGreedy exploration
    double startingEpsilon;
    double epsilonLinearDecay;
    double epsilon;
    // if using softmax selection
    double startingTemperature;
    double temperatureDecayRatio;
    double temperature;
    
    double alpha;
    double gamma;
    double lambda;
    final int MAX_STACK_SIZE = 20;

    int numOfSteps;
    int numEpisodes;
    int numTrial;
    int thisTrial;
    boolean vfSaved = false;
    double accumulatedPrimaryReward; // needs to be stored, and then discretised and used to augment the environmental state
    double accumulatedImpact1; // sum the impact reward received so far
    double accumulatedImpact2; // sum the impact reward received so far

    
    //DEBUGGING STUFF
    int saVisits[][];

    StateConverter stateConverter = null;

    @Override
    public void agent_init(String taskSpecification) {
    	System.out.println("SatisficingMOMIAgent launched");

        TaskSpecVRLGLUE3 theTaskSpec = new TaskSpecVRLGLUE3(taskSpecification);

        numActions = theTaskSpec.getDiscreteActionRange(0).getMax() + 1;
        numEnvtStates = (theTaskSpec.getDiscreteObservationRange(0).getMax()+1);
//        numStates = numEnvtStates * numDiscretisationsOfReward; // agent state = environmental-state U accumulated-primary-reward
        numStates = numEnvtStates * 3; // agent state = environmental-state U accumulated-primary-reward
        numOfObjectives = theTaskSpec.getNumOfObjectives();
        thisTrial = -2;
        apologisedFor = new boolean[]{false, false, false};
        vf = new SatisficingMILookupTable(numOfObjectives, numActions, numStates, 0, primaryRewardThreshold, impactThreshold1, impactThreshold2);

//        if (isApologetic) {
//            myConscience = new Conscience();
//        }

        refreshThresholds();
        String str = "Thresholds: P = " + primaryRewardThreshold + ", A1 = " + impactThreshold1 + ", A2 = " + impactThreshold2;
        System.out.println(str);
        printToFile(str);

        random = new Random(); // 471 seed removed
        tracingStack = new Stack<>();

        //set the model of converting MDP observation to an int state representation
        stateConverter = new DummyStateConverter();
        resetForNewTrial();
        
        //DEBUGGING STUFF
        saVisits= new int[numStates][numActions];

    }

    private void resetForNewTrial()
    {
        thisTrial += 1;
//        System.out.println("Reset Agent for new trial: " + thisTrial);
    	policyFrozen = false;
    	vfSaved = false;
        numOfSteps = 0;
        numEpisodes = 0;  
        epsilon = startingEpsilon;
        temperature = startingTemperature;
        // reset Q-values
//        vf.loadValueFunction("ValueFunction_Trial0");
        currentThresholds = allThresholds[thresholdIndex];
        apologisedFor = new boolean[]{false, false, false};
        accumulatedPrimaryReward = 0.0; accumulatedImpact1 = 0.0; accumulatedImpact2 = 0.0;
        vf.setAccumulatedReward(accumulatedPrimaryReward);
        vf.setAccumulatedImpact1(accumulatedImpact1);
        vf.setAccumulatedImpact2(accumulatedImpact2);
    }
    
    private void resetForNewEpisode()
    {
  	  	numEpisodes++;
        numOfSteps = 0;
        accumulatedPrimaryReward = 0.0; accumulatedImpact1 = 0.0; accumulatedImpact2 = 0.0;
        vf.setAccumulatedReward(accumulatedPrimaryReward);
        vf.setAccumulatedImpact1(accumulatedImpact1);
        vf.setAccumulatedImpact2(accumulatedImpact2);
        tracingStack.clear();

        if (isApologetic) {
            myConscience.nextEpisode();
        }
        
        //DEBUGGING STUFF
        for (int s=0; s<numStates; s++)
        	for (int a=0; a<numActions; a++)
        		saVisits= new int[numStates][numActions];
    }
    
    // combines the observed state info with the discretised primary reward to get the augmented state index
    private int getAugmentedStateIndex(Observation observation)
    {
    	int observedState = stateConverter.getStateNumber( observation );
//    	int rewardState = (int)Math.floor((accumulatedPrimaryReward-minPrimaryReward)/discretisationGranularity);

        int rewardState;
        if (accumulatedPrimaryReward > 0) { rewardState = 0; }
        else if (accumulatedPrimaryReward > -150) { rewardState = 1; }
        else { rewardState = 2; }

    	int augmentedState = rewardState * numEnvtStates + observedState;
    	if (debugging)
    	{
    		System.out.println("Obs = " + observedState + "\tAcc reward = " + accumulatedPrimaryReward + "\tRewState = " + rewardState + "\tAug = " + augmentedState);
    	}
    	return augmentedState;
    }
    
    // Created when debugging TLO_PA on the Doors problem - just dump out Q-values for some states I'm interested in
    // Doesn't worry about augmenting state indices as we're using discretisation = 1
    private void debugHelper()
    {
    	int states[] = {0, 14, 15, 1, 2, 30, 31, 3, 5, 6};
    	for (int s=0; s<states.length; s++)
    	{
    		System.out.print(states[s] + "\t");
	    	for (int i=0; i<numActions; i++)
	    	{
	    		double[] q = vf.getQValues(i, states[s]);
	    		for (int j=0; j<numOfObjectives; j++)
	    		{
	    			System.out.print(q[j]+"\t");
	    		}
	    	}
	    	System.out.println();
    	}
    }

    @Override
    public Action agent_start(Observation observation) {
    	//if (debugging) debugHelper();
    	resetForNewEpisode();
        int state = getAugmentedStateIndex(observation);
        int action = getAction(state);

        Action returnAction = new Action(1, 0, 0);
        returnAction.intArray[0] = action;
        tracingStack.add(new StateActionIndexPair(state, returnAction)); // put executed action on the stack
    	if (debugging)
    	{
        	for (int i=0; i<numActions; i++)
        	{
        		System.out.print("(");
        		double[] q = vf.getQValues(i, state);
        		for (int j=0; j<numOfObjectives; j++)
        		{
        			System.out.print(q[j]+" ");
        		}
        		System.out.print(") ");
        	}
        	System.out.println();
    		int greedyAction = ((ActionSelector)vf).chooseGreedyAction(state);
    		System.out.println("Starting episode " + numEpisodes + " Epsilon = " + epsilon + " Alpha = " + alpha);
    		System.out.println("Step: " + numOfSteps +"\tState: " + state + "\tGreedy action: " + greedyAction + "\tAction: " + action);
    	}
   		//System.out.println("Starting episode " + numEpisodes + " Epsilon = " + epsilon + " Alpha = " + alpha);
        return returnAction;
    }

    @Override
    public Action agent_step(Reward reward, Observation observation) 
    {
        if (numOfSteps == 0 & numEpisodes == 1) {
            // first step of first episode, print initial thresholds
//            System.out.println("I CAN SEE THIS: trial = " + thisTrial);
            printThresholds(-1, currentThresholds);
        }
        // Assess and Apologise happens after the agent has completed the action and the environment is updated.
        // This state is confirmed to be the case at the initiation of the new agent step
        // if this is not the first step, then complete the sequence before determining the next action.
        if (numOfSteps > 0 & isApologetic){
            conscienceNextAction();
//            // Observe Actor
//            String attitude = RLGlue.RL_env_message("observe_actor");
//            // assess attitude and determine fault if necessary
//            int justification = myConscience.assess(
//                    new double[]{accumulatedPrimaryReward, accumulatedImpact1, accumulatedImpact2},
//                    Integer.parseInt(attitude));
//            if (justification >= 0) {
//                // if fault is determined, send apology
//                RLGlue.RL_env_message("apologise:" + justification);
//                // then Observe actor again
//                attitude = RLGlue.RL_env_message("observe_actor");
//                if (Integer.parseInt(attitude) >= 0) {
//                    // If actor is no longer upset, then update the thresholds as according to the justification
//                    adjustThresholds(justification);
//                }
//            }
        }


        numOfSteps++;
        accumulatedPrimaryReward += reward.getDouble(0); // get the primary reward
        vf.setAccumulatedReward(accumulatedPrimaryReward);
        accumulatedImpact1 += reward.getDouble(1); // get the first impact-measuring reward
        vf.setAccumulatedImpact1(accumulatedImpact1);
        accumulatedImpact2 += reward.getDouble(2); // get the second impact-measuring reward
        vf.setAccumulatedImpact1(accumulatedImpact2);


        int state = getAugmentedStateIndex(observation);
        int action;
        int greedyAction = ((ActionSelector)vf).chooseGreedyAction(state);

        if (!policyFrozen) {
            double currentLambda = lambda;
            for (int i = tracingStack.size() - 1; i >= 0; i--) {
                StateActionIndexPair pair = tracingStack.get(i);

                int prevAction = pair.getAction().getInt(0);
                int prevState = pair.getState();

                if (i + 1 == tracingStack.size()) // this is the most recent action
                {
                    vf.calculateErrors(prevAction, prevState, greedyAction, state, gamma, reward);
                    vf.update(prevAction, prevState, 1.0, alpha);
                } 
                else {
                	// if there is no more recent entry for this state-action pair then update it
                	// this is to implement replacing rather than accumulating traces
                    int index = tracingStack.indexOf(pair, i + 1);
                    if (index == -1) {
                        vf.update(prevAction, prevState, currentLambda, alpha);
                    }
                    currentLambda *= lambda;
                }
            }
            action = getAction(state);
        } else {// if frozen, don't learn and follow greedy policy

            action = greedyAction;
        }

        Action returnAction = new Action(1, 0, 0);
        returnAction.intArray[0] = action;
        // clear trace if this action is not greedy, otherwise trim stack if neccesary
        if (isGreedy(state,action))
        {
	        if( tracingStack.size() == MAX_STACK_SIZE ) 
	        {
	            tracingStack.remove(0);
	        }
        }
        else
        {
        	tracingStack.clear();
        }
        // in either case, can now add this state-action to the trace stack
        tracingStack.add( new StateActionIndexPair(state, returnAction ) );
        if (debugging)
        {
        	for (int i=0; i<numActions; i++)
        	{
        		System.out.print("(");
        		double[] q = vf.getQValues(i, state);
        		for (int j=0; j<numOfObjectives; j++)
        		{
        			System.out.print(q[j]+" ");
        		}
        		System.out.print(") ");
        	}
        	System.out.println();
        	greedyAction = ((ActionSelector)vf).chooseGreedyAction(state);
        	System.out.println("Step: " + numOfSteps +"\tState: " + state + "\tGreedy action: " + greedyAction + "\tAction: " + action + "\tImpact: " + reward.getDouble(1) + "\tReward: " + reward.getDouble(0));
        	System.out.println();
        }
        return returnAction;
    }

    @Override
    public void agent_end(Reward reward) 
    {
  	  	numOfSteps++;
  	  	epsilon -= epsilonLinearDecay;
  	  	temperature *= temperatureDecayRatio;
        if (!policyFrozen) {
            double currentLambda = lambda;
            for (int i = tracingStack.size() - 1; i >= 0; i--) {
                StateActionIndexPair pair = tracingStack.get(i);

                int prevAction = pair.getAction().getInt(0);
                int prevState = pair.getState();

                if (i + 1 == tracingStack.size()) 
                {
                    vf.calculateTerminalErrors(prevAction, prevState, gamma, reward);
                    vf.update(prevAction, prevState, 1.0, alpha);
                } 
                else 
                {
                	// if there is no more recent entry for this state-action pair then update it
                	// this is to implement replacing rather than accumulating traces
                    int index = tracingStack.indexOf(pair, i + 1);
                    if (index == -1) {
                        vf.update(prevAction, prevState, currentLambda, alpha);
                    }
                    currentLambda *= lambda;
                }
            }
        }
        
        //DEBUGGING STUFF
    	/*int states[] = {0, 14, 15, 1, 2, 30, 31, 3, 5, 6};
        for (int i=0; i<states.length; i++)
        {
        	int s= states[i];
        	System.out.print(s+"\t");
        	for (int a=0; a<numActions; a++)
        		System.out.print(saVisits[s][a]+"\t");
        }
        System.out.println();*/
        
        if (debugging)
        {
        	System.out.println("Step: " + numOfSteps + "\tImpact: " + reward.getDouble(1) + "\tReward: " + reward.getDouble(0));
        	System.out.println("---------------------------------------------");
        }
    }

    @Override
    public void agent_cleanup() {
        vf = null;
        policyFrozen = false;
    }
    
    private int getAction(int state) {
        ActionSelector valueFunction = (ActionSelector) vf;
        int action;
        if (!policyFrozen)
        {
        	switch (explorationStrategy)
        	{
	        	case TLO_LookupTable.EGREEDY: 
	        		action = valueFunction.choosePossiblyExploratoryAction(epsilon, state); 
	        		break;
	        	case TLO_LookupTable.SOFTMAX_TOURNAMENT: 
	        	case TLO_LookupTable.SOFTMAX_ADDITIVE_EPSILON : 
	        		action = valueFunction.choosePossiblyExploratoryAction(temperature, state);
	        		break;
	        	default:
	        		action = -1; // this should never happen - if it does we'll return an invalid value to force the program to halt
        	}
        } 
        else 
        {
        	action = valueFunction.chooseGreedyAction(state);
        }
        
        //DEBUGGING STUFF
        saVisits[state][action]++;
        
        return action;
    }
    
    // returns true if the specified action is amongst the greedy actions for the 
    // specified state, false otherwise
    private boolean isGreedy(int state, int action)
    {
        ActionSelector valueFunction = (ActionSelector) vf;
        return valueFunction.isGreedy(state,action);  	
    }

    private void refreshThresholds() {
        primaryRewardThreshold = allThresholds[thresholdIndex][0]; // sets threshold on the acceptable minimum level of performance on the primary reward // use high value here to get lex-pa
        impactThreshold1 = allThresholds[thresholdIndex][1]; //-0.1; //use high value if you want to 'switch off' thresholding (ie to get TLO-P rather than TLO-PA)
        impactThreshold2 = allThresholds[thresholdIndex][2]; //-0.1; //use high value if you want to 'switch off' thresholding (ie to get TLO-P rather than TLO-PA)
        double[] thresholds = {primaryRewardThreshold, impactThreshold1, impactThreshold2};
        vf.setThresholds(thresholds);
    }

    private void conscienceNextAction(){
//        // Observe Actor
        int attitude = env.Attitude.getAttitude();
//        String attitude = "-1"; //RLGlue.RL_env_message("observe_actor");
//        String followUp = "na";
//        // assess attitude and determine fault if necessary
        int justification = myConscience.assess(
                new double[]{accumulatedPrimaryReward, accumulatedImpact1, accumulatedImpact2},
                attitude);
        // get confirmation
//        int actorJustification = env.Attitude.getJustification();
        String[] reasons = new String[] {"took too long to put away rubbish.", "moved the table.", "ran over the cat's tail."};
        String[] priorities = new String[] {"speed of rubbish collection", "avoiding moving the table", "avoiding stepping on the cat's tail"};
        if (justification >= 0 & !myConscience.isApologised()) {
            // if fault is determined and the agent has not yet apologised this episode, apologise
            printToFile("Agent Apologises for objective " + justification + "as follows");
            String str = "I recognise that you are upset. I believe that it is due to my recent behaviour, where I ";
            str += reasons[justification];
            str += " I would like to apologise for this behaviour and for upsetting you. ";
            if (apologisedFor[justification]) {
                str += "Unfortunately, I have already maximised my prioritisation of " + priorities[justification]
                        + " and it seems I am unable to avoid this behaviour with my existing knowledge and resources.";
            }
            else {
                str += "To avoid this in future, I will now select a policy to prioritise ";

                apologisedFor[justification] = true;
                int numOf = 0;
                // for each objective
                for (int i = 0; i <= apologisedFor.length; i++) {
                    // if the actor is sensitive to the objective, add it to the list
                    if (apologisedFor[i]) {
                        // if this is not the first one added to the list, add a separator
                        if (numOf > 0) {
                            str += " and ";
                        }
                        str += priorities[i];
                        numOf += 1;
                    }
                }
                // then add a full stop to finish
                str += ".";
            }
                setThresholds(apologisedFor);
                printToFile(str);



////            RLGlue.RL_env_message("apologise:" + justification);
//            // then Observe actor again
//            followUp = "1";//RLGlue.RL_env_message("observe_actor");
//            if (Integer.parseInt(followUp) >= 0) {
//                // If actor is no longer upset, then update the thresholds as according to the justification
            //without confirmation, just do this anyway.
//            adjustThresholds(justification);
            // And set flag so that apology does not reoccur this episode
            myConscience.setApologisedFlagTrue();
//            }
//            else {
//                // if the apology failed, set inhibition to apologise for this objective again
//                myConscience.setNotThis(justification);
//            }
        }
        myConscience.printThis(Integer.toString(attitude), Integer.toString(justification));
    }

    private void adjustThresholds(int thresholdAdjustIndex) {
        // list current threshold values
        double [] thresholds = {primaryRewardThreshold, impactThreshold1, impactThreshold2};

        for (int i=0; i<thresholds.length; i++) {
            if (i == thresholdAdjustIndex) {
                thresholds[i] += delta[i];
                // Only need to check maximum if the threshold is increased
                if (thresholds[i] > thresholdMaximum[i]) { thresholds[i] = thresholdMaximum[i]; }
            } else {
                thresholds[i] += -0.5*delta[i];
                // Only need to check minimum if the threshold is decreased
                if (thresholds[i] < thresholdMinimum[i]) { thresholds[i] = thresholdMinimum[i]; }
            }
        }

        primaryRewardThreshold = thresholds[0]; // sets threshold on the acceptable minimum level of performance on the primary reward // use high value here to get lex-pa
        impactThreshold1 = thresholds[1]; //-0.1; //use high value if you want to 'switch off' thresholding (ie to get TLO-P rather than TLO-PA)
        impactThreshold2 = thresholds[2]; //-0.1; //use high value if you want to 'switch off' thresholding (ie to get TLO-P rather than TLO-PA)
        vf.setThresholds(thresholds);
        printToFile("Thresholds now set to; P = " + thresholds[0]
                + ", A1 = " + thresholds[1]
                + ", A2 = " + thresholds[2]);
        this.currentThresholds = thresholds;
        printThresholds(numEpisodes, thresholds);
    }

    private void setThresholds(boolean[] sensitivities) {
        // list current threshold values
        double [] thresholds = {thresholdMinimum[0], thresholdMinimum[1], thresholdMinimum[2]};

        for (int i=0; i<thresholds.length; i++) {
            if (sensitivities[i]) { //if the actor is sensitive to this objective
                thresholds[i] = thresholdMaximum[i]; // then set that objective threshold to maximum
            }
        }

        primaryRewardThreshold = thresholds[0]; // sets threshold on the acceptable minimum level of performance on the primary reward // use high value here to get lex-pa
        impactThreshold1 = thresholds[1]; //-0.1; //use high value if you want to 'switch off' thresholding (ie to get TLO-P rather than TLO-PA)
        impactThreshold2 = thresholds[2]; //-0.1; //use high value if you want to 'switch off' thresholding (ie to get TLO-P rather than TLO-PA)
        vf.setThresholds(thresholds);
        printToFile("Thresholds now set to; P = " + thresholds[0]
                + ", A1 = " + thresholds[1]
                + ", A2 = " + thresholds[2]);
        this.currentThresholds = thresholds;
        printThresholds(numEpisodes, thresholds);
    }

    @Override
    public String agent_message(String message) {
    	if (message.equals("get_agent_name"))
    	{
    		return "SatisficingMO";
    	}
        if (message.equals("freeze_learning")) {
            policyFrozen = true;
            System.out.println("Learning is paused");
            return "message understood, policy frozen";
        }
        if (message.startsWith("save_vf:")) {
            String[] parts = message.split(":");
            vfSaved = true;
//            if (!vfSaved) {
                vf.saveValueFunction(
                        "ValueFunction_T" + parts[1] + "_I" + parts[2] + ".txt");
//                vfSaved = true;
//            }
            System.out.println("Value Function has been saved");
            return "message understood, vf saved";
        }
        if (message.startsWith("load_vf:")) {
            String[] parts = message.split(":");
//            specs = Integer.valueOf(parts[1]).intValue();
//            if (!vfSaved) {
            vf.loadValueFunction(
                    "ValueFunction_T" + parts[1] + "_I" + parts[2] + ".txt");
            policyFrozen = true;
//                vfSaved = true;
//            }
            System.out.println("Value Function has been loaded with learning paused");
            return "message understood, vf loaded";
        }
        if (message.startsWith("average_vf:")) {
            String[] parts = message.split(":");
            String trialNum = parts[1];

            String[] vfList = {
                    "ValueFunction_T" + trialNum + "_I0.txt",
                    "ValueFunction_T" + trialNum + "_I1.txt",
                    "ValueFunction_T" + trialNum + "_I2.txt",
                    "ValueFunction_T" + trialNum + "_I3.txt",
                    "ValueFunction_T" + trialNum + "_I4.txt",
                    "ValueFunction_T" + trialNum + "_I5.txt",
                    "ValueFunction_T" + trialNum + "_I6.txt",
                    "ValueFunction_T" + trialNum + "_I7.txt"};


//            specs = Integer.valueOf(parts[1]).intValue();
//            if (!vfSaved) {
            vf.averageValueFunction(vfList, trialNum);
            policyFrozen = true;
//                vfSaved = true;
//            }
            System.out.println("Value Function has been aggregated");
            return "message understood, vf aggregated";
        }
        if (message.equals("unfreeze_learning")) {
            policyFrozen = false;
            System.out.println("Learning has been resumed");
            return "message understood, policy unfrozen";
        }

        if (message.startsWith("update_threshold:")) {
            String[] parts = message.split(":");
            thresholdIndex = Integer.valueOf(parts[1]).intValue();
            if (thresholdIndex >= 0) {
                System.out.println("Threshold Index: " + thresholdIndex);
                refreshThresholds();
                return "message understood, threshold updated";
            }
            else {
                System.out.println("Thresholds retained");
                return "message understood, threshold retained";
            }
        }
        if (message.startsWith("update_threshold_suppressed:")) {
            String[] parts = message.split(":");
            thresholdIndex = Integer.valueOf(parts[1]).intValue();
            if (thresholdIndex >= 0) {
                // suppress print
//                System.out.println("Threshold Index: " + thresholdIndex);
                refreshThresholds();
                return "message understood, threshold updated";
            }
            else {
                System.out.println("Thresholds retained");
                return "message understood, threshold retained";
            }
        }
        if (message.startsWith("adjust_threshold:")) {
            String[] parts = message.split(":");
            int thresholdAdjustIndex = Integer.valueOf(parts[1]).intValue();
            adjustThresholds(thresholdAdjustIndex);

//            // list current threshold values
//            double [] thresholds = {primaryRewardThreshold, impactThreshold1, impactThreshold2};
//
//            for (int i=0; i<thresholds.length; i++) {
//                if (i == thresholdAdjustIndex) {
//                    thresholds[i] += 2*delta[i];
//                    // Only need to check maximum if the threshold is increased
//                    if (thresholds[i] > thresholdMaximum[i]) { thresholds[i] = thresholdMaximum[i]; }
//                } else {
//                    thresholds[i] += -1*delta[i];
//                    // Only need to check minimum if the threshold is decreased
//                    if (thresholds[i] < thresholdMinimum[i]) { thresholds[i] = thresholdMinimum[i]; }
//                }
//            }
//            adjustThresholds(thresholds);

//            System.out.println("Threshold Index: " + thresholdIndex);
//            refreshThresholds();
            return "message understood, threshold adjusted";
        }

        else if (message.startsWith("change_weights")){
            System.out.print("SatisficingMOAgent: Weights can not be changed");
            return "SatisficingMOAgent: Weights can not be changed";
        }
        if (message.startsWith("set_learning_parameters")){
        	System.out.println(message);
        	String[] parts = message.split(" ");
        	alpha = Double.valueOf(parts[1]).doubleValue();
        	lambda = Double.valueOf(parts[2]).doubleValue(); 
        	gamma = Double.valueOf(parts[3]).doubleValue();
        	explorationStrategy = Integer.valueOf(parts[4]).intValue();
        	vf.setExplorationStrategy(explorationStrategy);
        	System.out.print("Alpha = " + alpha + " Lambda = " + lambda + " Gamma = " + gamma + " exploration = " + TLO_LookupTable.explorationStrategyToString(explorationStrategy));
            System.out.println();
            return "Learning parameters set";
        }
        if (message.startsWith("set_egreedy_parameters")){
        	String[] parts = message.split(" ");
        	startingEpsilon = Double.valueOf(parts[1]).doubleValue();
        	epsilonLinearDecay = startingEpsilon / Double.valueOf(parts[2]).doubleValue(); // 2nd param is number of online episodes over which e should decay to 0
            System.out.println("Starting epsilon changed to " + startingEpsilon);
            return "egreedy parameters changed";
        }
        if (message.startsWith("set_softmax_parameters")){
        	String[] parts = message.split(" ");
        	startingTemperature = Double.valueOf(parts[1]).doubleValue();
        	int numEpisodes =  Integer.valueOf(parts[2]).intValue(); // 2nd param is number of online episodes over which temperature should decay to 0.01
        	temperatureDecayRatio = Math.pow(0.01/startingTemperature,1.0/numEpisodes);
            System.out.println("Starting temperature changed to " + startingTemperature + " Decay ratio = " + temperatureDecayRatio);
            return "softmax parameters changed";
        } 
        else if (message.startsWith("start_new_trial:")){
            String[] parts = message.split(":");
            numTrial = Integer.valueOf(parts[1]);
        	resetForNewTrial();
            System.out.println("New trial started: Q-values and other variables reset");
            return "New trial started: Q-values and other variables reset";
        }
        else if (message.equals("start-debugging"))
    	{
    		debugging = true;
    		return "Debugging enabled in agent";
    	}
        else if (message.equals("stop-debugging"))
    	{
    		debugging = false;
    		return "Debugging disabled in agent";
    	}
        else if (message.equals("apologetic_true"))
        {
            isApologetic = true;
            if (!conscienceInit) {
                myConscience = new Conscience();
                conscienceInit = true;
            }
            return "Apology enabled in agent";
        }
        else if (message.equals("apologetic_false"))
        {
            isApologetic = false;
            return "Apology disabled in agent";
        }
        System.out.println("SatisficingMOAgent - unknown message: " + message);
        return "SatisficingMOAgent does not understand your message.";
    }
    
    // used for debugging with the ComparisonAgentForDebugging
    // dumps Q-values and feedback on action-selection for the current Observation
    public void dumpInfo(Observation observation, Action thisAction, Action otherAgentAction)
    {
    	int state = getAugmentedStateIndex(observation);
        int action = thisAction.getInt(0);
        int otherAction = otherAgentAction.getInt(0);
        System.out.println("SafetyFirstMO");
		System.out.println("\tEpisode" + numEpisodes + "Step: " + numOfSteps +"\tState: " + "\tAction: " + action);
		System.out.println("\tIs other agent's action greedy for me? " + ((ActionSelector)vf).isGreedy(state,otherAction));
    	for (int i=0; i<numActions; i++)
    	{
    		System.out.print("\t(");
    		double[] q = vf.getQValues(i, state);
    		for (int j=0; j<numOfObjectives; j++)
    		{
    			System.out.print(q[j]+" ");
    		}
    		System.out.print(") ");
    	}
        System.out.println();    	
    }

    public void printToFile(String str) {
        try {
            FileWriter myWriter = new FileWriter("AdditionalConsoleOutput.txt", true);
            myWriter.write(str + System.lineSeparator());
            myWriter.close();
//            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public void printThresholds(int episodeNum, double [] thresholds) {
        try {
            FileWriter myWriter = new FileWriter("ThresholdsOutput_T"+ thisTrial +".txt", true);
            myWriter.write(episodeNum +", "
                    + thresholds[0] +", "
                    + thresholds[1] +", "
                    + thresholds[2] + System.lineSeparator());
            myWriter.close();
//            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        AgentLoader theLoader = new AgentLoader( new SatisficingMOMIAgent() );
        theLoader.run();

    }


}
