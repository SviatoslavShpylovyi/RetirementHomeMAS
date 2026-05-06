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


import java.util.List;



public class Engine {

    private static final ExecutorService jadeExecutor = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        try {
            final Runtime runtime = Runtime.instance();
            final Profile profile = new ProfileImpl();

            final ContainerController container =
                    jadeExecutor.submit(() -> runtime.createMainContainer(profile)).get();

            runGUI(container);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AgentContainerException("MainContainer", e);
        } catch (ExecutionException e) {
            throw new AgentContainerException("MainContainer", e);
        }

    }

    public static void runGUI(final ContainerController mainContainer) {
        try {
            final AgentController guiAgent = mainContainer.createNewAgent("rma", "jade.tools.rma.rma", new Object[0]);
            guiAgent.start();
        } catch (final StaleProxyException e) {
            throw new AgentContainerException("GUIAgent", e);
        }
    }

    public static void runAgent(final ContainerController mainContainer, final String agentName,
                                final String className, final String packageName) {
        try {
            final String path = format("org.example.%s.agents.%s", packageName, className);
            final AgentController agent = mainContainer.createNewAgent(agentName, path, new Object[] {});
            agent.start();
        } catch (final StaleProxyException e) {
            throw new AgentContainerException(agentName, e);
        }
    }

    public static void runAgent(final ContainerController mainContainer, final String agentName,
                                final String className, final String packageName, final Object[] args) {
        try {
            final String path = format("org.example.%s.agents.%s", packageName, className);
            final AgentController agent = mainContainer.createNewAgent(agentName, path, args);
            agent.start();
        } catch (final StaleProxyException e) {
            throw new AgentContainerException(agentName, e);
        }
    }


}