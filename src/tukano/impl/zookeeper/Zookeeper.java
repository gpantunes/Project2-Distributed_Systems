
package tukano.impl.zookeeper;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.SynchronousQueue;
import java.util.logging.Logger;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

public class Zookeeper implements Watcher {

	private static Logger Log = Logger.getLogger(Zookeeper.class.getName());

	private ZooKeeper _client;
	private final int TIMEOUT = 5000;
	
	private String leaderNode;

	public Zookeeper(String servers) throws Exception {
		this.connect(servers, TIMEOUT);
	}

	public synchronized ZooKeeper client() {
		if (_client == null || !_client.getState().equals(ZooKeeper.States.CONNECTED)) {
			throw new IllegalStateException("ZooKeeper is not connected.");
		}
		return _client;
	}

	public void registerWatcher(Watcher w) {
		client().register(w);
	}

	private void connect(String host, int timeout) throws IOException, InterruptedException {
		var connectedSignal = new CountDownLatch(1);
		_client = new ZooKeeper(host, TIMEOUT, (e) -> {
			if (e.getState().equals(Watcher.Event.KeeperState.SyncConnected)) {
				connectedSignal.countDown();
			}
		});
		connectedSignal.await();
	}

	public String createNode(String path, byte[] data, CreateMode mode) {
		try {
			return client().create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, mode);
		} catch (KeeperException.NodeExistsException x) {
			return path;
		} catch (Exception x) {
			throw new RuntimeException(x);
		}
	}

	public List<String> getChildren(String path) {
		try {
			return client().getChildren(path, false);
		} catch (Exception x) {
			throw new RuntimeException(x);
		}
	}

	public List<String> getAndWatchChildren(String path) {
		try {
			return client().getChildren(path, true);
		} catch (Exception x) {
			throw new RuntimeException(x);
		}

	}

	@Override
	public void process(WatchedEvent event) {
		try {
			if (event.getType() == EventType.NodeChildrenChanged || event.getType() == EventType.NodeDeleted) {

				var path = event.getPath();
				Log.info("Got a path changed event:" + path);
				var children = getChildren( path );
				Log.info("Updated children:" + getChildren( path ));
				getAndWatchChildren(path);
				
				Collections.sort(children);
				Log.info("New primary" + children.get(0));
			}
		} catch ( Exception x) {
			x.printStackTrace();
		}
	}

	public String getPrimaryReplicaUri() {
        try {
            List<String> children = _client.getChildren("/primary", false);
            Collections.sort(children);
            String primaryNode = children.get(0);
            byte[] data = _client.getData("/primary/" + primaryNode, false, null);
            return new String(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get primary replica URI", e);
        }
    }
	
	public String getPrimary(String path) {
        try {
        	
            List<String> children = getAndWatchChildren("/" + path);
            Collections.sort(children);
            
            String primaryNode = children.get(0);
            byte[] data = getNodeData("/" + path +"/" + primaryNode);
            
            return new String(data);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
	
	public byte[] getNodeData(String path) {
        try {
            Stat stat = client().exists(path, false);
            
            if (stat != null) {
            	return client().getData(path, false, stat);
            } else {
                throw new KeeperException.NoNodeException("Node does not exist: " + path);
            }
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

}