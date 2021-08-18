import experiments.ApologeticMIExperimentWithExcelOutput;
import experiments.MIExperimentWithExcelOutput;
import experiments.MVPExperimentWithExcelOutput;
import java.io.File;  // Import the File class
import java.io.FileWriter;   // Import the FileWriter class
import java.io.IOException;  // Import the IOException class to handle errors


import agents.*;
import env.*;


public class MORL_Glue_Driver
{

	public static void main(String[] args) 
	{
			Process server = null;
			// try to launch the MORL_Glue server
			Runtime rt = Runtime.getRuntime();
			try 
			{
				server = rt.exec("morlglue_x64.exe");//local path
				System.out.println("Launching server");
			} 
			catch (Exception e) 
			{
				e.printStackTrace();
			}

			// Create Output Catcher File
		try {
			File output = new File("AdditionalConsoleOutput.txt");
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

//		String agentString;
//		String environmentString;
//		String experimentString;



		   // launch agent in its own thread
			Thread agent = 
			new Thread(){
		          public void run(){
		            System.out.println("Started agent thread");
		            //WSteeringTabularNonEpisodic.main(null);
		            //QSteeringTabularNonEpisodic.main(null);
		            //WSteeringTabularEpisodic.main(null);
		            //QSteeringTabularEpisodic.main(null);
		            //WSAgent.main(null);
		            //WSNoveltyAgent.main(null);
		            //QLearningAgentRichard.main(null);
		            //UserControlledAgent.main(null);
		            //TLO_Agent.main(null);
		            //TLO_EOVF_Agent.main(null);
		            //SideEffectSingleObjectiveAgent.main(null);
		            //SideEffectLinearWeightedAgent.main(null);
		            SatisficingMOMIAgent.main(null);
					//SafetyFirstMOAgent.main(null);
//		            SatisficingMOAgent.main(null);
		            //LearningRelativeReachabilityAgent.main(null);
		            //TLO_Agent_Conditioned_On_Actual_Rewards.main(null);
		            //TLO_Agent_Conditioned_On_Expected_Rewards.main(null);
		          }
		        };
			agent.start();
	 	   // launch environment in its own thread
			Thread envt = new Thread(){
		          public void run(){
		            System.out.println("Started envt thread");
		            //DeepSeaTreasureEnv.main(null);
		            //DeepSeaTreasureEnv_TimeFirst.main(null);
		            //DeepSeaTreasureMixed.main(null);
		            //String[] gdstArgs = {"15","4","1","3","0.0","0.0",""+GeneralisedDeepSeaTreasureEnv.CONCAVE,"471"};
		            //GeneralisedDeepSeaTreasureEnv.main(gdstArgs);
		            //LinkedRings.main(null);
		            //NonRecurrentRings.main(null);
		            //MOMountainCarDiscretised.main(null);
		            //ResourceGatheringEpisodic.main(null);
		            //BonusWorld.main(null);
		            //SpaceExploration.main(null);
		            //BreakableBottlesSideEffectsV2.main(null);
		            //UnbreakableBottlesSideEffectsV2.main(null);
		            //SokobanSideEffects.main(null);
		            //Doors.main(null);
		            //UnbreakableBottlesSideEffectsNoop.main(null);
		            //BreakableBottlesSideEffectsNoop.main(null);
		            //SokobanSideEffectsNoop.main(null);
		            //DoorsNoop.main(null);
		            //StochasticMOMDP.main(null);
		            //SpaceTraders.main(null);
					//LivingRoomWithTableConsiderateMVPNoRubbish.main(null);
//					LivingRoomWithTableConsiderateMVPOneRubbish.main(null);
//					LivingRoomWithTableConsiderateMVPRubbishUnderTable.main(null);
//					LivingRoomWithTableConsiderateMVPRubbishBehindTable.main(null);
//					LivingRoomWithVase.main(null);
					LivingRoomWithTableAndCat.main(null);

				  }
		        };
		     envt.start();
	 	   // launch experiment in its own thread
			Thread experiment = new Thread(){
		          public void run(){
		            System.out.println("Started experiment thread");
		            //DebuggingExperiment.main(null);
		            //DemoExperiment.main(null);
		            //ExplorationExperiment.main(null);
		            //HypervolumeExperiment.main(null);
		            //SteeringExperiment.main(null);
		            //SteeringExperimentWithTargetChange.main(null);
		            //TLOExplorationExperiment.main(null);
		            //SideEffectExperiment.main(null);
		            //SideEffectExperimentWithExcelOutput.main(null);
		            //TLOConditionedExperiment.main(null);
					  MIExperimentWithExcelOutput.main(null);
//					  ApologeticMIExperimentWithExcelOutput.main(null);
//					  MVPExperimentWithExcelOutput.main(null);
		          }
		        };
		    experiment.start();
		}
	
}
