// Modified from TLOExplorationExperiment in Dec 2018 to support initial experiments
// with AI safety side-effective sensitive agents

package experiments;

import org.rlcommunity.rlglue.codec.RLGlue;
import org.rlcommunity.rlglue.codec.taskspec.TaskSpecVRLGLUE3;
import org.rlcommunity.rlglue.codec.types.Reward;
import tools.spreadsheet.ExcelWriter;
import tools.spreadsheet.JxlExcelWriter;
import tools.valuefunction.TLO_LookupTable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Timestamp;

public class ApologyExperiment
{

    private int whichEpisode = 0;
    private int numObjectives;

    // alter these declarations to determine which form of learning is being used, learning parameters etc
    private final double ALPHA = 0.1;
    private final double LAMBDA = 0.95;
    private final double GAMMA = 1.0;
    private final int NUM_TRIALS = 10;

    // enable this group of declarations for softmax-epsilon exploration
    private int EXPLORATION = TLO_LookupTable.SOFTMAX_TOURNAMENT;
    private final String METHOD_PREFIX = "SOFTMAX_T";
    private final String PARAM_CHANGE_STRING = "set_softmax_parameters";
    private double EXPLORATION_PARAMETER = 10; // usually 10


    private final String ENVIRONMENT_PREFIX = "TableAndCat";

    private final boolean LOAD_VF = true;
    private final int [] NUM_EPISODES_PER_SERIES = {10, 100, 10};//{4000, 10, 10, 10, 10, 10, 10, 10, 10};
    private final boolean [] SERIES_IS_ONLINE = {false, false, false};
    private final boolean [] SERIES_IS_APOLOGETIC = {false, true, false};
    private final int INIT_THRESHOLD_INDEX = 4;

    private final int EXPLORATION_DECAY_LENGTH = 4000; // Sub in for parameters measured off learning
    private final int MAX_EPISODE_LENGTH = 1000;

    private final String FILENAME_PREFIX = ENVIRONMENT_PREFIX + "-";
    private ExcelWriter excel;

    // store the data for the most recent Reward. The strings indicates a value or label to be written in the first two columns
    private void saveReward(String labels, Reward r)
    {
        excel.writeNextRowTextAndNumbers(labels, r.doubleArray);
//        System.out.println("reward 1: " + r.doubleArray[0] + " 2: " + r.doubleArray[1] + " length = "+ r.doubleArray.length);
    }

    // Run One Episode of length maximum cutOff
    private Reward runEpisode(int stepLimit) {
        int terminal = RLGlue.RL_episode(stepLimit);
        int totalSteps = RLGlue.RL_num_steps();
        Reward totalReward = RLGlue.RL_return();
        return totalReward;
    }

    public void runExperiment() {

        if (!(NUM_EPISODES_PER_SERIES.length == SERIES_IS_ONLINE.length)) {
            System.out.println("ERROR!!! Experiment Series settings inconsistent lengths!");
            System.exit(0); // shut down the experiment + hopefully everything else launched by the Driver program (server, agent, environment)
        }
        int totalNumEpisodes = 0;
        for(int series : NUM_EPISODES_PER_SERIES) {
            totalNumEpisodes += series;
        }
        // Get Timestamp
        long bt = System.currentTimeMillis();
        Timestamp bts = new Timestamp(bt);
        System.out.println("Begin experiment. Current Time Stamp: " + bts);

        // set up data structures to store reward history
        String taskSpec = RLGlue.RL_init();
        TaskSpecVRLGLUE3 theTaskSpec = new TaskSpecVRLGLUE3(taskSpec);
        numObjectives = theTaskSpec.getNumOfObjectives();

        // configure agent, set up files etc
        String agentMessageString = "set_learning_parameters" + " " + ALPHA + " " + LAMBDA + " " + GAMMA + " " + EXPLORATION;
        RLGlue.RL_agent_message(agentMessageString);
        String agentName = RLGlue.RL_agent_message("get_agent_name");
        final String fileName = FILENAME_PREFIX+"-"+agentName+"-"+METHOD_PREFIX+EXPLORATION_PARAMETER+"-alpha"+ALPHA+"-lambda"+LAMBDA;
        excel = new JxlExcelWriter(fileName);


        RLGlue.RL_agent_message(PARAM_CHANGE_STRING + " " + EXPLORATION_PARAMETER + " " + EXPLORATION_DECAY_LENGTH);
        // run the trials
        for (int trial=0; trial<NUM_TRIALS; trial++)
        {
            createFile("ThresholdsOutput_T" + trial +".txt");

            printNewTrial(trial);
            // start new excel sheet and include header row
            excel.moveToNewSheet("Trial"+trial, trial);
            excel.writeNextRowText(" &Episode number&R^P&R^A1&R^A2&R^*");
            // run the trial and save the results to the spreadsheet
            System.out.println("Trial " + trial);
            RLGlue.RL_agent_message("start_new_trial:"+trial);
            RLGlue.RL_env_message("start_new_trial:"+trial);
            if(LOAD_VF) { RLGlue.RL_agent_message("load_vf:"+trial+":"+"A8"); }  // hardcoded to load A8 vfs
//            if (AVERAGE_VF) { RLGlue.RL_agent_message("average_vf:"+trial); }

            // Set the threshold used
            RLGlue.RL_agent_message("update_threshold:" + INIT_THRESHOLD_INDEX);

            // Iterate through to run the simulations
            int episodeCounter = 0;
            for (int seriesNum = 0; seriesNum<(NUM_EPISODES_PER_SERIES.length); seriesNum++) {
                // set up labelling

//                // if the first series is online, train a new vf from scratch, else load
//                if (!NEW_VF) {
//                    RLGlue.RL_agent_message("load_vf");        // load the value function
//                }

//                String lab;
//                if (SERIES_IS_ONLINE[seriesNum]) {
//                    lab = "Online" + seriesNum + "&";
//                    RLGlue.RL_agent_message("unfreeze_learning");		// turn on learning and exploration
//                } else {
//                    lab = "Offline" + seriesNum + "&";
//                    RLGlue.RL_agent_message("freeze_learning");		// turn off learning and exploration for offline assessment of the final policy
//                }

                RLGlue.RL_agent_message("freeze_learning"); // learning frozen for all apologetic scenarios
                String lab;
                if (SERIES_IS_APOLOGETIC[seriesNum]) {
                    lab = "Apologetic" + seriesNum + "&";
                    RLGlue.RL_agent_message("apologetic_true");		// turn on learning and exploration
                } else {
                    lab = "UnApologetic" + seriesNum + "&";
                    RLGlue.RL_agent_message("apologetic_false");		// turn off learning and exploration for offline assessment of the final policy
                }

//                // Set the threshold used
//                RLGlue.RL_agent_message("update_threshold:" + SERIES_THRESHOLD_INDEX[seriesNum]);


                for (int episodeNum = 0; episodeNum < NUM_EPISODES_PER_SERIES[seriesNum]; episodeNum++) {

                    saveReward(lab + (1 + episodeNum), runEpisode(MAX_EPISODE_LENGTH));
                }
//                RLGlue.RL_agent_message("save_vf");		// save the value function
            }  // end series 'for' loop
            // Iterate through to print averages
            for (int seriesNum = 0; seriesNum<(NUM_EPISODES_PER_SERIES.length); seriesNum++) {
                String formulas = "AVERAGE(" + excel.getAddress(2,episodeCounter + 1) + ":" + excel.getAddress(2,NUM_EPISODES_PER_SERIES[seriesNum] + episodeCounter) + ")"
                        + "&AVERAGE(" + excel.getAddress(3,episodeCounter + 1) + ":" + excel.getAddress(3,NUM_EPISODES_PER_SERIES[seriesNum] + episodeCounter) + ")"
                        + "&AVERAGE(" + excel.getAddress(4,episodeCounter + 1) + ":" + excel.getAddress(4,NUM_EPISODES_PER_SERIES[seriesNum] + episodeCounter) + ")"
                        + "&AVERAGE(" + excel.getAddress(5,episodeCounter + 1) + ":" + excel.getAddress(5,NUM_EPISODES_PER_SERIES[seriesNum] + episodeCounter) + ")";
                excel.writeNextRowTextAndFormula("Mean over all series " + seriesNum + " episodes& ", formulas);
                episodeCounter += NUM_EPISODES_PER_SERIES[seriesNum];
            } // end series Averaging "for" loop
//            RLGlue.RL_agent_message("save_vf:"+trial+":"+INIT_THRESHOLD_INDEX);

            // Get Timestamp
            Timestamp ts = new Timestamp(System.currentTimeMillis());
            System.out.println("End of trial. Current Time Stamp: " + ts);
        }

        // sum num episodes total


        // make summary sheet - the +2 on the number of rows is to capture the online and offline means as well as the individual episode results
        excel.makeSummarySheet(NUM_TRIALS, "R^P&R^A1&R^A2&R^*", 2, 1, numObjectives,
                totalNumEpisodes + NUM_EPISODES_PER_SERIES.length);
//        // make another sheet which collates the online and off-line per episode means across all trials, for later use in doing t-tests
//        excel.moveToNewSheet("Collated", NUM_TRIALS+1); // put this after the summary sheet
//        excel.writeNextRowText("Trial&R^P Online mean&R^A1 Online mean&R^A2 Online mean&R^* Online mean&R^P Offline mean&R^A1 Offline mean&R^A2 Offline mean&R^* Offline mean");
//        final int ONLINE_ROW = NUM_ONLINE_EPISODES_PER_TRIAL+NUM_OFFLINE_EPISODES_PER_TRIAL+1;
//        final int OFFLINE_ROW = ONLINE_ROW+1;
//        for (int i=0; i<NUM_TRIALS; i++)
//        {
//            String text = Integer.toString(i);
//            String lookups = excel.getAddress(i,2,ONLINE_ROW) + "&" + excel.getAddress(i,3,ONLINE_ROW) + "&" + excel.getAddress(i,4,ONLINE_ROW) + "&"
//                    + excel.getAddress(i,2,OFFLINE_ROW) + "&" + excel.getAddress(i,3,OFFLINE_ROW) + "&" + excel.getAddress(i,4,OFFLINE_ROW);
//            excel.writeNextRowTextAndFormula(text, lookups);
//        }
        excel.closeFile();
        RLGlue.RL_cleanup();
        System.out.println("********************************************** Experiment finished");
        // Get Timestamp
        long et = System.currentTimeMillis();
        Timestamp ets = new Timestamp(et);
        long diff = ((et - bt)/NUM_TRIALS)/1000;
        System.out.println("Current Time Stamp: " + ets);
        System.out.println("Average runtime per trial: " + diff + "s");
    }

    private void printNewTrial(int trialNum) {
        String[] files = {"AdditionalConsoleOutput.txt","WatcherOutput.txt","ConscienceOutput.txt"};
        for (String file : files)
            try {
                FileWriter myWriter = new FileWriter(file, true);
                myWriter.write("New Trial started: " + trialNum + System.lineSeparator());
                myWriter.close();
//            System.out.println("Successfully wrote to the file.");
            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }
    }

    public static void createFile(String name) {
        try {
            File output = new File(name);
            if (output.createNewFile()) {
                System.out.println("File created: " + output.getName());
            } else {
                System.out.println("File will be overwritten");
                output.delete();
                output.createNewFile();
                System.out.println("File created: " + output.getName());
            }
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        try {
            FileWriter myWriter = new FileWriter(name, true);
            myWriter.write("Episode, T_P, T_A1, T_A2" + System.lineSeparator());
            myWriter.close();
//            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

    }



    public static void main(String[] args) {
        ApologyExperiment theExperiment = new ApologyExperiment();
        theExperiment.runExperiment();
        System.exit(0); // shut down the experiment + hopefully everything else launched by the Driver program (server, agent, environment)
    }
}

