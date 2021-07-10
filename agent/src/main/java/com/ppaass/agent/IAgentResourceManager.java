package com.ppaass.agent;

/**
 * The agent resource manager.
 */
public interface IAgentResourceManager {
    /**
     * Prepare the resource that an agent requires for running.
     */
    void prepareResources();

    /**
     * Destroy the resource that a agent requires for running.
     */
    void destroyResources();
}
