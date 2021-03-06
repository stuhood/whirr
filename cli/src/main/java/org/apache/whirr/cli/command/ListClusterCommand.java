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

package org.apache.whirr.cli.command;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

import org.apache.whirr.service.ClusterSpec;
import org.apache.whirr.service.Service;
import org.apache.whirr.service.ServiceFactory;
import org.jclouds.compute.domain.NodeMetadata;

/**
 * A command to list the nodes in a cluster.
 */
public class ListClusterCommand extends AbstractClusterSpecCommand {

  public ListClusterCommand() throws IOException {
    this(new ServiceFactory());
  }

  public ListClusterCommand(ServiceFactory factory) {
    super("list-cluster", "List the nodes in a cluster.", factory);
  }
  
  @Override
  public int run(InputStream in, PrintStream out, PrintStream err,
      List<String> args) throws Exception {
    
    OptionSet optionSet = parser.parse(args.toArray(new String[0]));

    if (!optionSet.nonOptionArguments().isEmpty()) {
      printUsage(parser, err);
      return -1;
    }
    try {
      ClusterSpec clusterSpec = getClusterSpec(optionSet);

      Service service = factory.create(clusterSpec.getServiceName());
      Set<? extends NodeMetadata> nodes = service.getNodes(clusterSpec);
      for (NodeMetadata node : nodes) {
        out.println(Joiner.on('\t').join(node.getId(), node.getImageId(),
            getFirstAddress(node.getPublicAddresses()),
            getFirstAddress(node.getPrivateAddresses()),
            node.getState(), node.getLocation().getId()));
      }
      return 0;
    } catch (IllegalArgumentException e) {
      err.println(e.getMessage());
      printUsage(parser, err);
      return -1;
    }
  }
  
  private String getFirstAddress(Set<String> addresses) {
    return addresses.isEmpty() ? "" : Iterables.get(addresses, 0);
  }

  private void printUsage(OptionParser parser, PrintStream stream) throws IOException {
    stream.println("Usage: whirr list-cluster [OPTIONS]");
    stream.println();
    parser.printHelpOn(stream);
  }
}
