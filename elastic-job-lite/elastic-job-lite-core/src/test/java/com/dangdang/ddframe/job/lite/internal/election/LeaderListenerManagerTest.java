/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.job.lite.internal.election;

import com.dangdang.ddframe.job.lite.api.strategy.JobInstance;
import com.dangdang.ddframe.job.lite.internal.election.LeaderListenerManager.LeaderElectionJobListener;
import com.dangdang.ddframe.job.lite.internal.schedule.JobRegistry;
import com.dangdang.ddframe.job.lite.internal.server.ServerService;
import com.dangdang.ddframe.job.lite.internal.server.ServerStatus;
import com.dangdang.ddframe.job.lite.internal.storage.JobNodeStorage;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.unitils.util.ReflectionUtils;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class LeaderListenerManagerTest {
    
    @Mock
    private JobNodeStorage jobNodeStorage;
    
    @Mock
    private LeaderService leaderService;
    
    @Mock
    private ServerService serverService;
    
    private final LeaderListenerManager leaderListenerManager = new LeaderListenerManager(null, "test_job");
    
    @BeforeClass
    public static void setUpJobInstance() {
        JobRegistry.getInstance().addJobInstance("test_job", new JobInstance("127.0.0.1@-@0"));
    }
    
    @Before
    public void setUp() throws NoSuchFieldException {
        MockitoAnnotations.initMocks(this);
        ReflectionUtils.setFieldValue(leaderListenerManager, leaderListenerManager.getClass().getSuperclass().getDeclaredField("jobNodeStorage"), jobNodeStorage);
        ReflectionUtils.setFieldValue(leaderListenerManager, "leaderService", leaderService);
        ReflectionUtils.setFieldValue(leaderListenerManager, "serverService", serverService);
    }
    
    @Test
    public void assertStart() {
        leaderListenerManager.start();
        verify(jobNodeStorage).addDataListener(Matchers.<LeaderElectionJobListener>any());
    }
    
    @Test
    public void assertIsNotLeaderInstancePathAndServerPath() {
        String path = "/test_job/leader/election/other";
        leaderListenerManager.new LeaderElectionJobListener().dataChanged(null, new TreeCacheEvent(TreeCacheEvent.Type.NODE_REMOVED, new ChildData(path, null, "127.0.0.1".getBytes())), path);
        verify(leaderService, times(0)).electLeader();
    }
    
    @Test
    public void assertLeaderElectionWhenAddLeaderInstancePath() {
        String path = "/test_job/leader/election/instance";
        leaderListenerManager.new LeaderElectionJobListener().dataChanged(null, new TreeCacheEvent(TreeCacheEvent.Type.NODE_ADDED, new ChildData(path, null, "127.0.0.1".getBytes())), path);
        verify(leaderService, times(0)).electLeader();
    }
    
    @Test
    public void assertLeaderElectionWhenRemoveLeaderInstancePathWithoutAvailableServers() {
        String path = "/test_job/leader/election/instance";
        leaderListenerManager.new LeaderElectionJobListener().dataChanged(null, new TreeCacheEvent(TreeCacheEvent.Type.NODE_REMOVED, new ChildData(path, null, "127.0.0.1".getBytes())), path);
        verify(leaderService, times(0)).electLeader();
    }
    
    @Test
    public void assertLeaderElectionWhenRemoveLeaderInstancePathWithAvailableServer() {
        when(serverService.isAvailableServer("127.0.0.1")).thenReturn(true);
        String path = "/test_job/leader/election/instance";
        leaderListenerManager.new LeaderElectionJobListener().dataChanged(null, new TreeCacheEvent(TreeCacheEvent.Type.NODE_REMOVED, new ChildData(path, null, "127.0.0.1".getBytes())), path);
        verify(leaderService).electLeader();
    }
    
    @Test
    public void assertLeaderElectionWhenServerDisableWithoutLeader() {
        String path = "/test_job/servers/127.0.0.1";
        leaderListenerManager.new LeaderElectionJobListener().dataChanged(
                null, new TreeCacheEvent(TreeCacheEvent.Type.NODE_ADDED, new ChildData(path, null, ServerStatus.DISABLED.name().getBytes())), path);
        verify(leaderService, times(0)).electLeader();
    }
    
    @Test
    public void assertLeaderElectionWhenServerEnableWithLeader() {
        when(leaderService.hasLeader()).thenReturn(true);
        String path = "/test_job/servers/127.0.0.1";
        leaderListenerManager.new LeaderElectionJobListener().dataChanged(null, new TreeCacheEvent(TreeCacheEvent.Type.NODE_UPDATED, new ChildData(path, null, "".getBytes())), path);
        verify(leaderService, times(0)).electLeader();
    }
    
    @Test
    public void assertLeaderElectionWhenServerEnableWithoutLeader() {
        String path = "/test_job/servers/127.0.0.1";
        leaderListenerManager.new LeaderElectionJobListener().dataChanged(null, new TreeCacheEvent(TreeCacheEvent.Type.NODE_UPDATED, new ChildData(path, null, "".getBytes())), path);
        verify(leaderService).electLeader();
    }
    
    @Test
    public void assertLeaderRemoveWhenFollowerDisable() {
        String path = "/test_job/servers/127.0.0.1";
        leaderListenerManager.new LeaderElectionJobListener().dataChanged(
                null, new TreeCacheEvent(TreeCacheEvent.Type.NODE_UPDATED, new ChildData(path, null, ServerStatus.DISABLED.name().getBytes())), path);
        verify(leaderService, times(0)).removeLeader();
    }
    
    @Test
    public void assertLeaderRemoveWhenLeaderDisable() {
        when(leaderService.isLeader()).thenReturn(true);
        String path = "/test_job/servers/127.0.0.1";
        leaderListenerManager.new LeaderElectionJobListener().dataChanged(
                null, new TreeCacheEvent(TreeCacheEvent.Type.NODE_UPDATED, new ChildData(path, null, ServerStatus.DISABLED.name().getBytes())), path);
        verify(leaderService).removeLeader();
    }
}
