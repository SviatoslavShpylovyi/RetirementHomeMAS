package org.example;

import static java.lang.String.format;

import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.example.exceptions.AgentContainerException;
import org.example.exceptions.JadePlatformInitializationException;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;
import org.example.model.ActivityType;
import org.example.model.HealthProfile;
import org.example.model.MobilityLevel;
import org.example.model.Resident;


import java.util.List;



public class Engine {

    private static final ExecutorService jadeExecutor = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        try {
            System.out.println("Starting retirement home MAS demo...");

            Runtime runtime = Runtime.instance();

            Profile profile = new ProfileImpl();
            profile.setParameter(Profile.GUI, "true");

            jade.wrapper.AgentContainer container = runtime.createMainContainer(profile);

            Resident anna = new Resident(
                    true,
                    new HealthProfile(
                            MobilityLevel.LOW,
                            List.of("Avoid long walking")
                    ),
                    List.of(ActivityType.MUSIC, ActivityType.SOCIAL_TEA, ActivityType.BINGO),
                    "R1",
                    "Anna"
            );

            Resident jan = new Resident(
                    true,
                    new HealthProfile(
                            MobilityLevel.MEDIUM,
                            List.of()
                    ),
                    List.of(ActivityType.BOARD_GAMES, ActivityType.POKER, ActivityType.READING),
                    "R2",
                    "Jan"
            );

            Resident maria = new Resident(
                    false,
                    new HealthProfile(
                            MobilityLevel.HIGH,
                            List.of("Sensitive to noise")
                    ),
                    List.of(ActivityType.ART, ActivityType.KNITTING, ActivityType.MOVIE),
                    "R3",
                    "Maria"
            );

            AgentController annaAgent = container.createNewAgent(
                    "resident-anna",
                    "org.example.agents.ResidentAgent",
                    new Object[]{anna}
            );

            AgentController janAgent = container.createNewAgent(
                    "resident-jan",
                    "org.example.agents.ResidentAgent",
                    new Object[]{jan}
            );

            AgentController mariaAgent = container.createNewAgent(
                    "resident-maria",
                    "org.example.agents.ResidentAgent",
                    new Object[]{maria}
            );

            AgentController activityAgent = container.createNewAgent(
                    "activity-agent",
                    "org.example.agents.ActivityAgent",
                    null
            );

            AgentController resourceAgent = container.createNewAgent(
                    "resource-agent",
                    "org.example.agents.ResourceAgent",
                    null
            );

            AgentController healthAgent = container.createNewAgent(
                    "health-agent",
                    "org.example.agents.HealthAgent",
                    null
            );

            AgentController socialSupportAgent = container.createNewAgent(
                    "social-support-agent",
                    "org.example.agents.SocialSupportAgent",
                    null
            );

            AgentController scenarioAgent = container.createNewAgent(
                    "scenario-agent",
                    "org.example.agents.ScenarioAgent",
                    null
            );

            AgentController facilitatorAgent = container.createNewAgent(
                    "facilitator-agent",
                    "org.example.agents.FacilitatorAgent",
                    null
            );

            annaAgent.start();
            janAgent.start();
            mariaAgent.start();

            activityAgent.start();
            resourceAgent.start();
            healthAgent.start();
            socialSupportAgent.start();
            scenarioAgent.start();
            facilitatorAgent.start();

            System.out.println("Demo agents started successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}