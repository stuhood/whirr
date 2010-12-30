/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.whirr.service.cassandra;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.commons.configuration.Configuration;
import org.apache.whirr.service.Cluster;
import org.apache.whirr.service.Cluster.Instance;
import org.apache.whirr.service.ClusterActionEvent;
import org.apache.whirr.service.ClusterActionHandlerSupport;
import org.apache.whirr.service.ClusterSpec;
import org.apache.whirr.service.ComputeServiceContextBuilder;
import org.apache.whirr.service.jclouds.FirewallSettings;
import org.jclouds.compute.ComputeServiceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CassandraClusterActionHandler extends ClusterActionHandlerSupport {
  
  private static final Logger LOG =
    LoggerFactory.getLogger(CassandraClusterActionHandler.class);
    
  public static final String CASSANDRA_ROLE = "cassandra";
  public static final int CLIENT_PORT = 9160;
  public static final int JMX_PORT = 8080;

  public static final String BIN_TARBALL = "whirr.cassandra.tarball.url";
  public static final String MAJOR_VERSION = "whirr.cassandra.version.major";

  @Override
  public String getRole() {
    return CASSANDRA_ROLE;
  }
  
  @Override
  protected void beforeBootstrap(ClusterActionEvent event) throws IOException {
    addRunUrl(event, "sun/java/install");
    Configuration config = event.getClusterSpec().getConfiguration();
    String tarball = config.getString(BIN_TARBALL, null);
    String major = config.getString(MAJOR_VERSION, null);
    if (tarball != null && major != null)
      addRunUrl(event, String.format("apache/cassandra/install %s %s", major, tarball));
    else
      addRunUrl(event, "apache/cassandra/install");
  }

  @Override
  protected void beforeConfigure(ClusterActionEvent event)
      throws IOException, InterruptedException {
    ClusterSpec clusterSpec = event.getClusterSpec();
    Cluster cluster = event.getCluster();
    LOG.info("Authorizing firewall");
    ComputeServiceContext computeServiceContext =
      ComputeServiceContextBuilder.build(clusterSpec);
    FirewallSettings.authorizeIngress(computeServiceContext,
        cluster.getInstances(), clusterSpec, CLIENT_PORT);
    FirewallSettings.authorizeIngress(computeServiceContext,
        cluster.getInstances(), clusterSpec, JMX_PORT);
    
    List<Instance> seeds = getSeeds(cluster.getInstances());
    String servers = Joiner.on(' ').join(getPrivateIps(seeds));
    addRunUrl(event, "apache/cassandra/post-configure",
        "-c", clusterSpec.getProvider(), servers);
  }

  private List<String> getPrivateIps(List<Instance> instances) {
    return Lists.transform(Lists.newArrayList(instances),
        new Function<Instance, String>() {
      @Override
      public String apply(Instance instance) {
        return instance.getPrivateAddress().getHostAddress();
      }
    });
  }
  
  /**
   * Pick a selection of the nodes that are to become seeds. TODO improve
   * selection method. Right now it picks 20% of the nodes as seeds, or a
   * minimum of one node if it is a small cluster.
   * 
   * @param nodes
   *          all nodes in cluster
   * @return list of seeds
   */
  protected List<Instance> getSeeds(Set<Instance> instances) {
    List<Instance> nodes = Lists.newArrayList(instances);
    int seeds = (int) Math.ceil(Math.max(1, instances.size() * 0.2));
    List<Instance> rv = Lists.newArrayList();
    for (int i = 0; i < seeds; i++) {
      rv.add(nodes.get(i));
    }
    return rv;
  }

}
