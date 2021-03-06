// A modified version of the TLO_LookupTable created to support our potential-based
// side-effect impact minimisation agent
// Designed specifically for the side-ffects problems, so it maximises the value of the first objective
// subject to meeting the threshold value for the (second objective + accumulated
// reward for the second objective) - i.e. maximise goal reward subject to minimising impact
package tools.valuefunction;

import tools.valuefunction.interfaces.ActionSelector;
import tools.valuefunction.interfaces.LookupTable;

import java.io.*;

public class SafetyFirstMILookupTable extends LookupTable implements ActionSelector
{
    double thisStateValues[][];
    double summedImpact;
    double thresholds[];

    public SafetyFirstMILookupTable(int numberOfObjectives, int numberOfActions, int numberOfStates, int initValue, double[] threshold)
    {
        super(numberOfObjectives, numberOfActions, numberOfStates, initValue);
//        if (numberOfObjectives!=3)
//        	System.out.println("ERROR!!! Don't use SafetyFirstLookupTable for problems other than side-effects ones.");
        thresholds = new double[1];
        thresholds[0] = threshold[0]; // only the impact measures should be thresholded (second declared below)
        thresholds[1] = threshold[1]; // only the impact measure should be thresholded
        thisStateValues = new double[numberOfActions][3]; // leave out the performance objective to avoid any risk of accidentally using it in action selection
//        thisStateValues = new double[numberOfActions][2]; // leave out the performance objective to avoid any risk of accidentally using it in action selection
    }
    
       
    // for debugging purposes - print out Q- values for all actions for the current state
    public void printCurrentStateValues(int state)
    {
    	getActionValues(state); // copy the action values into the 2D array thisStateValues
    	System.out.print("State " + state + ": ");
		for (int a=0; a<numberOfActions; a++)
		{
			System.out.print("a"+a+" (");
			for (int obj=0; obj<numberOfObjectives; obj++)
			{
				System.out.print(thisStateValues[a][obj]+",");
			}
			System.out.print(") ");
		}  
		System.out.println();
    }
    
    // This is a bit of a hack to get around the fact that the structure of Rustam's lookup table doesn't map nicely
    // on to my TLO library functions. The whole Agent and ValueFunction structure of Rustam's code needs to be refactored at some point
    // This also re-orders the objectives, adds the accumulated impact on, so as to
    // meet the requirements of the side-effect minimising agent
    private void getActionValues(int state)
    {
		for (int a=0; a<numberOfActions; a++)
		{
			// copy the impact-reward values + accumulated impact into the first field
			thisStateValues[a][0] = valueFunction.get(1)[a][state] + summedImpact;
			// copy the goal-reward into the second field
			thisStateValues[a][1] = valueFunction.get(0)[a][state];
			// ignore the performance-reward values as we shouldn't have access to them anyway
		}
    }
    
    public void setAccumulatedImpact(double accumulatedImpact)
    {
    	summedImpact = accumulatedImpact;
    }

    @Override
    public int chooseGreedyAction(int state) 
    {
    	getActionValues(state);
    	return TLO.greedyAction(thisStateValues, thresholds); 
    }
    
    // returns true if action is amongst the greedy actions for the specified
    // state, otherwise false
    public boolean isGreedy(int state, int action)
    {  
    	getActionValues(state);
    	int best = TLO.greedyAction(thisStateValues, thresholds); 
    	// this action is greedy if it is TLO-equal to the greedily selected action
    	return (TLO.compare(thisStateValues[action], thisStateValues[best], thresholds)==0);
    }
       //*****
    // softmax selection based on tournament score (i.e. the number of actions which each action TLO-dominates)
    protected int softmaxTournament(double temperature, int state)
    {
    	int best = chooseGreedyAction(state); // as a side-effect this will also set up the Q-values array
    	double scores[] = TLO.getDominanceScore(thisStateValues,thresholds);
    	return Softmax.getAction(scores,temperature,best);
    }
    
    // softmax selection based on each action's additive epsilon score
    protected int softmaxAdditiveEpsilon(double temperature, int state)
    {
    	int best = chooseGreedyAction(state); // as a side-effect this will also set up the Q-values array
    	double scores[] = TLO.getInverseAdditiveEpsilonScore(thisStateValues,best);
    	return Softmax.getAction(scores,temperature,best);
    }    

    public double[] getThresholds() {
        return thresholds;
    }

    public void setThresholds(double[] thresholds) {
        this.thresholds = thresholds;
    }
    
     public void saveValueFunction(String theFileName) {
    	System.out.println(theFileName);
        for (int s = 0; s < numberOfStates; s++) {
            for (int a = 0; a < numberOfActions; a++) {
            	System.out.print("State "+s+"\tAction "+a+"\t");         	
            	for (int i = 0; i < numberOfObjectives; i++) {
                    System.out.print(valueFunction.get(i)[a][s] +"\t");
                }
            	System.out.println();
            }                
        }  	
        try {
            DataOutputStream DO = new DataOutputStream(new FileOutputStream(new File(theFileName)));
            for (int i = 0; i < numberOfObjectives; i++) {
                for (int a = 0; a < numberOfActions; a++) {
                    for (int s = 0; s < numberOfStates; s++) {
                        DO.writeDouble( valueFunction.get(i)[a][s] );
                    }
                }                
            }
            DO.close();
        } catch (FileNotFoundException ex) {
            System.err.println("Problem saving value function to file: " + theFileName + " :: " + ex);
        } catch (IOException ex) {
            System.err.println("Problem writing value function to file:: " + ex);
        }
    }
    
    public void loadValueFunction(String theFileName) {
        try {
            DataInputStream DI = new DataInputStream(new FileInputStream(new File(theFileName)));
            for (int i = 0; i < numberOfObjectives; i++) {
                for (int a = 0; a < numberOfActions; a++) {
                    for (int s = 0; s < numberOfStates; s++) {
                        valueFunction.get(i)[a][s] = DI.readDouble();
                    }
                }                
            }
            DI.close();
        } catch (FileNotFoundException ex) {
            System.err.println("Problem loading value function from file: " + theFileName + " :: " + ex);
        } catch (IOException ex) {
            System.err.println("Problem reading value function from file:: " + ex);
        }
    }
      
}