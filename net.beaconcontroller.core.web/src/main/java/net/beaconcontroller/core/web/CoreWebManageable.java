package net.beaconcontroller.core.web;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.Thread.State;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.beaconcontroller.core.IBeaconProvider;
import net.beaconcontroller.core.IOFMessageListener;
import net.beaconcontroller.core.IOFSwitch;
import net.beaconcontroller.core.types.MacVlanPair;
import net.beaconcontroller.counter.CounterValue;
import net.beaconcontroller.counter.ICounter;
import net.beaconcontroller.counter.ICounterStoreProvider;
import net.beaconcontroller.counter.ICounterStoreProvider.NetworkLayer;
import net.beaconcontroller.util.BundleAction;
import net.beaconcontroller.web.IWebManageable;
import net.beaconcontroller.web.view.BeaconJsonView;
import net.beaconcontroller.web.view.BeaconViewResolver;
import net.beaconcontroller.web.view.Tab;
import net.beaconcontroller.web.view.layout.Layout;
import net.beaconcontroller.web.view.layout.OneColumnLayout;
import net.beaconcontroller.web.view.layout.TwoColumnLayout;
import net.beaconcontroller.web.view.section.JspSection;
import net.beaconcontroller.web.view.section.StringSection;
import net.beaconcontroller.web.view.section.TableSection;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.statistics.OFPortStatisticsRequest;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFAggregateStatisticsRequest;
import org.openflow.protocol.statistics.OFQueueStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.packageadmin.PackageAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.osgi.context.BundleContextAware;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.View;

/**
 * This class sets up the web UI component for the structures in
 * net.beaconcontroller.core and related "platform" related input/output.
 * 
 * It uses the net.beaconcontroller.web mgmt framework /
 * 
 * @author Kyle Forster (kyle.forster@bigswitch.com)
 * @author David Erickson (daviderickson@cs.stanford.edu)
 * 
 */
@Controller
@RequestMapping("/core")
public class CoreWebManageable implements BundleContextAware, IWebManageable {
    protected static Logger log = LoggerFactory.getLogger(CoreWebManageable.class);
    protected IBeaconProvider beaconProvider;
    protected BundleContext bundleContext;
    protected PackageAdmin packageAdmin;
    protected ICounterStoreProvider counterStore;
    protected List<Tab> tabs;

    public enum REQUESTTYPE {
        OFSTATS,
        OFFEATURES,
        SWITCHTABLE
    }
    
    public CoreWebManageable() {
        tabs = new ArrayList<Tab>();
        tabs.add(new Tab("Overview", "/wm/core/overview.do"));
        tabs.add(new Tab("OSGi", "/wm/core/osgi.do"));
    }

    /**
     * The bundleContext to set (platform level stuff)
     */
    @Autowired
    @Override
    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    /**
     * 
     */
    @Autowired
    public void setBeaconProvider(IBeaconProvider beaconProvider) {
        this.beaconProvider = beaconProvider;
    }

    /**
     * 
     */
    @Autowired
    public void setCounterStore(ICounterStoreProvider counterStore) {
        this.counterStore = counterStore;
    }
    
    @Override
    public String getName() {
        return "Core";
    }

    @Override
    public String getDescription() {
        return "Controls the core components of Beacon.";
    }

    @Override
    public List<Tab> getTabs() {
        return tabs;
    }

    @RequestMapping("/overview")
    public String overview(Map<String, Object> model) {
        Layout layout = new TwoColumnLayout();
        model.put("layout", layout);

        // Description
        layout.addSection(
                new StringSection("Welcome",
                        "Thanks for using Beacon!"),
                TwoColumnLayout.COLUMN1);

        // Switch List Table
        model.put("title", "Switches");
        model.put("switches", beaconProvider.getSwitches().values());
        layout.addSection(new JspSection("switches.jsp", model), TwoColumnLayout.COLUMN2);

        // Listener List Table
        List<String> columnNames = new ArrayList<String>();
        List<List<String>> cells = new ArrayList<List<String>>();
        columnNames = new ArrayList<String>();
        columnNames.add("OpenFlow Packet Type");
        columnNames.add("Listeners");
        cells = new ArrayList<List<String>>();
        for (Entry<OFType, List<IOFMessageListener>> entry : beaconProvider.getListeners().entrySet()) {
            List<String> row = new ArrayList<String>();
            row.add(entry.getKey().toString());
            StringBuffer sb = new StringBuffer();
            for (IOFMessageListener listener : entry.getValue()) {
                sb.append(listener.getName() + " ");
            }
            row.add(sb.toString());
            cells.add(row);
        }
        layout.addSection(new TableSection("OpenFlow Packet Listeners", columnNames, cells), TwoColumnLayout.COLUMN1);

        return BeaconViewResolver.SIMPLE_VIEW;
    }

    @RequestMapping("/osgi")
    public String osgi(Map<String, Object> model) {
        Layout layout = new OneColumnLayout();
        model.put("layout", layout);

        // Bundle Form
        model.put("title", "Add Bundle");
        layout.addSection(new JspSection("addBundle.jsp", new HashMap<String, Object>(model)), TwoColumnLayout.COLUMN1);

        // Bundle List Table
        model.put("bundles", Arrays.asList(this.bundleContext.getBundles()));
        model.put("title", "OSGi Bundles");
        layout.addSection(new JspSection("bundles.jsp", model), TwoColumnLayout.COLUMN1);

        return BeaconViewResolver.SIMPLE_VIEW;
    }

    @RequestMapping("/bundle/{bundleId}/{action}")
    @ResponseBody
    public String osgiAction(@PathVariable Long bundleId, @PathVariable String action) {
        final Bundle bundle = this.bundleContext.getBundle(bundleId);
        if (action != null) {
            try {
                if (BundleAction.START.toString().equalsIgnoreCase(action)) {
                    bundle.start();
                } else if (BundleAction.STOP.toString().equalsIgnoreCase(action)) {
                    bundle.stop();
                } else if (BundleAction.UNINSTALL.toString().equalsIgnoreCase(action)) {
                    bundle.uninstall();
                } else if (BundleAction.REFRESH.toString().equalsIgnoreCase(action)) {
                    packageAdmin.refreshPackages(new Bundle[] {bundle});
                }
            } catch (BundleException e) {
                log.error("Failure performing action " + action + " on bundle " + bundle.getSymbolicName(), e);
            }
        }
        return "";
    }

    @RequestMapping(value = "/bundle/add", method = RequestMethod.POST)
    public View osgiBundleAdd(@RequestParam("file") MultipartFile file, Map<String, Object> model) throws Exception {
        BeaconJsonView view = new BeaconJsonView();

        File tempFile = null;
        Bundle newBundle = null;
        try {
            tempFile = File.createTempFile("beacon", ".jar");
            file.transferTo(tempFile);
            tempFile.deleteOnExit();
            newBundle = bundleContext.installBundle("file:"+tempFile.getCanonicalPath());
            model.put(BeaconJsonView.ROOT_OBJECT_KEY,
                    "Successfully installed: " + newBundle.getSymbolicName()
                            + "_" + newBundle.getVersion());
        } catch (IOException e) {
            log.error("Failure to create temporary file", e);
            model.put(BeaconJsonView.ROOT_OBJECT_KEY, "Failed to install bundle.");
        } catch (BundleException e) {
            log.error("Failure installing bundle", e);
            model.put(BeaconJsonView.ROOT_OBJECT_KEY, "Failed to install bundle.");
        }
        view.setContentType("text/javascript");
        return view;
    }
    
    protected List<OFStatistics> getSwitchStatistics(long switchId, OFStatisticsType statType) {
        IOFSwitch sw = beaconProvider.getSwitches().get(switchId);
        Future<List<OFStatistics>> future;
        List<OFStatistics> values = null;
        if (sw != null) {
            OFStatisticsRequest req = new OFStatisticsRequest();
            req.setStatisticType(statType);
            int requestLength = req.getLengthU();
            if (statType == OFStatisticsType.FLOW) {
                OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
                OFMatch match = new OFMatch();
                match.setWildcards(0xffffffff);
                specificReq.setMatch(match);
                specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
                specificReq.setTableId((byte) 0xff);
                req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
                requestLength += specificReq.getLength();
            } else if (statType == OFStatisticsType.AGGREGATE) {
                OFAggregateStatisticsRequest specificReq = new OFAggregateStatisticsRequest();
                OFMatch match = new OFMatch();
                match.setWildcards(0xffffffff);
                specificReq.setMatch(match);
                specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
                specificReq.setTableId((byte) 0xff);
                req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
                requestLength += specificReq.getLength();
            } else if (statType == OFStatisticsType.PORT) {
                OFPortStatisticsRequest specificReq = new OFPortStatisticsRequest();
                specificReq.setPortNumber((short)OFPort.OFPP_NONE.getValue());
                req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
                requestLength += specificReq.getLength();
            } else if (statType == OFStatisticsType.QUEUE) {
                OFQueueStatisticsRequest specificReq = new OFQueueStatisticsRequest();
                specificReq.setPortNumber((short)OFPort.OFPP_ALL.getValue());
                // LOOK! openflowj does not define OFPQ_ALL! pulled this from openflow.h
                // note that I haven't seen this work yet though...
                specificReq.setQueueId(0xffffffff);
                req.setStatistics(Collections.singletonList((OFStatistics)specificReq));
                requestLength += specificReq.getLength();
            } else if (statType == OFStatisticsType.DESC ||
                       statType == OFStatisticsType.TABLE) {
                // pass - nothing todo besides set the type above
            }
            req.setLengthU(requestLength);
            try {
                future = sw.getStatistics(req);
                values = future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Failure retrieving statistics from switch {}", sw, e);
            }
        }
        return values;
    }
    protected List<OFStatistics> getSwitchStatistics(String switchId, OFStatisticsType statType) {
        return getSwitchStatistics(HexString.toLong(switchId), statType);
    }

    @RequestMapping("/switch/{switchId}/flows")
    public String getSwitchFlows(@PathVariable String switchId, Map<String,Object> model) {
        OneColumnLayout layout = new OneColumnLayout();
        model.put("title", "Flows for switch: " + switchId);
        model.put("layout", layout);
        model.put("flows", getSwitchStatistics(switchId, OFStatisticsType.FLOW));
        layout.addSection(new JspSection("flows.jsp", model), null);
        return BeaconViewResolver.SIMPLE_VIEW;
    }
    
    @RequestMapping("/switch/all/{statType}/json")
    public View getAllSwitchStatisticsJson(@PathVariable String statType, Map<String,Object> model) {
        BeaconJsonView view = new BeaconJsonView();
        OFStatisticsType type = null;
        REQUESTTYPE rType = null;
        
        if (statType.equals("port")) {
            type = OFStatisticsType.PORT;
            rType = REQUESTTYPE.OFSTATS;
        } else if (statType.equals("queue")) {
            type = OFStatisticsType.QUEUE;
            rType = REQUESTTYPE.OFSTATS;
        } else if (statType.equals("flow")) {
            type = OFStatisticsType.FLOW;
            rType = REQUESTTYPE.OFSTATS;
        } else if (statType.equals("aggregate")) {
            type = OFStatisticsType.AGGREGATE;
            rType = REQUESTTYPE.OFSTATS;
        } else if (statType.equals("desc")) {
            type = OFStatisticsType.DESC;
            rType = REQUESTTYPE.OFSTATS;
        } else if (statType.equals("table")) {
            type = OFStatisticsType.TABLE;
            rType = REQUESTTYPE.OFSTATS;
        } else if (statType.equals("features")) {
            rType = REQUESTTYPE.OFFEATURES;
        } else if (statType.equals("host")) {
            rType = REQUESTTYPE.SWITCHTABLE;
        } else {
            return view;
        }
        
        Long[] switchDpids = beaconProvider.getSwitches().keySet().toArray(new Long[0]);
        List<GetConcurrentStatsThread> activeThreads = new ArrayList<GetConcurrentStatsThread>(switchDpids.length);
        List<GetConcurrentStatsThread> pendingRemovalThreads = new ArrayList<GetConcurrentStatsThread>();
        GetConcurrentStatsThread t;
        for (Long l : switchDpids) {
            t = new GetConcurrentStatsThread(l, rType, type);
            activeThreads.add(t);
            t.start();
        }
        
        /*
         * Join all the threads after the timeout. Set a hard timeout
         * of 12 seconds for the threads to finish. If the thread has not
         * finished the switch has not replied yet and therefore we won't 
         * add the switch's stats to the reply.
         */
        for (int iSleepCycles = 0; iSleepCycles < 12; iSleepCycles++) {
            for (GetConcurrentStatsThread curThread : activeThreads) {
                if (curThread.getState() == State.TERMINATED) {
                    if (rType == REQUESTTYPE.OFSTATS) {
                        model.put(HexString.toHexString(curThread.getSwitchId()), curThread.getStatisticsReply());
                    } else if (rType == REQUESTTYPE.OFFEATURES) {
                        model.put(HexString.toHexString(curThread.getSwitchId()), curThread.getFeaturesReply());
                    } else if (rType == REQUESTTYPE.SWITCHTABLE) {
                        List<Map<String, Long>> switchTableJson = new ArrayList<Map<String, Long>>();
                        Iterator<MacVlanPair> iterSwitchTable = curThread.getSwitchTable().keySet().iterator();
                        while (iterSwitchTable.hasNext()) {
                            MacVlanPair key = iterSwitchTable.next();
                            Map<String, Long> switchTableEntry = new HashMap<String, Long>();
                            switchTableEntry.put("mac", key.mac);
                            switchTableEntry.put("vlan", (long) key.vlan);
                            switchTableEntry.put("port", (long) curThread.getSwitchTable().get(key));
                            switchTableJson.add(switchTableEntry);
                        }
                        model.put(HexString.toHexString(curThread.getSwitchId()), switchTableJson   );
                    }
                    pendingRemovalThreads.add(curThread);
                }
            }
            
            // remove the threads that have completed the queries to the switches
            for (GetConcurrentStatsThread curThread : pendingRemovalThreads) {
                activeThreads.remove(curThread);
            }
            // clear the list so we don't try to double remove them
            pendingRemovalThreads.clear();
            
            // if we are done finish early so we don't always get the worst case
            if (activeThreads.isEmpty()) {
                break;
            }
            
            // sleep for 1 s here
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                System.out.println("CoreWebManageable thread failed to sleep!"); 
                e.printStackTrace();
            }
        }
        
        return view;
    }
    
    @RequestMapping("/switch/{switchId}/{statType}/json")
    public View getSwitchStatisticsJson(@PathVariable String switchId, @PathVariable String statType, Map<String,Object> model) {
        BeaconJsonView view = new BeaconJsonView();
        List<OFStatistics> values = null;
        if (statType.equals("port")) {
            values = getSwitchStatistics(switchId, OFStatisticsType.PORT);
        } else if (statType.equals("queue")) {
            values = getSwitchStatistics(switchId, OFStatisticsType.QUEUE);
        } else if (statType.equals("flow")) {
            values = getSwitchStatistics(switchId, OFStatisticsType.FLOW);
        } else if (statType.equals("aggregate")) {
            values = getSwitchStatistics(switchId, OFStatisticsType.AGGREGATE);
        } else if (statType.equals("desc")) {
            values = getSwitchStatistics(switchId, OFStatisticsType.DESC);
        } else if (statType.equals("table")) {
            values = getSwitchStatistics(switchId, OFStatisticsType.TABLE);
        } else if (statType.equals("features")) {
            IOFSwitch sw = beaconProvider.getSwitches().get(HexString.toLong(switchId));
            if (sw != null) {
                OFFeaturesReply fr = sw.getFeaturesReply();
                model.put(HexString.toHexString(sw.getId()), fr);
            }
            return view;
        } else if (statType.equals("host")) {
            IOFSwitch sw = beaconProvider.getSwitches().get(HexString.toLong(switchId));
            if (sw != null) {
                List<Map<String, Long>> switchTableJson = new ArrayList<Map<String, Long>>();
                Map<MacVlanPair, Short> swTable = sw.getMacVlanToPortMap();
                Iterator<MacVlanPair> iterSwitchTable = swTable.keySet().iterator();
                while (iterSwitchTable.hasNext()) {
                    MacVlanPair key = iterSwitchTable.next();
                    Map<String, Long> switchTableEntry = new HashMap<String, Long>();
                    switchTableEntry.put("mac", key.mac);
                    switchTableEntry.put("vlan", (long) key.vlan);
                    switchTableEntry.put("port", (long) swTable.get(key));
                    switchTableJson.add(switchTableEntry);
                }
                model.put(HexString.toHexString(sw.getId()), switchTableJson);
            }
            return view;
        }
        // this will only format as a correct DPID if the full DPID is given,
        // otherwise it will just use the decimal representation (i.e. whatever switchId is)
        model.put(switchId, values);
        return view;
    }
    
    @RequestMapping("/controller/switches/json")
    public View getSwitchesJson(Map<String,Object> model) {
        BeaconJsonView view = new BeaconJsonView();
        List<Map<String,Long>> switchIds = new ArrayList<Map<String,Long>>();
        for (IOFSwitch s:beaconProvider.getSwitches().values()) {
            Map<String, Long> m = new HashMap<String, Long>();
            m.put("dpid", s.getId());
            switchIds.add(m);
        }
       model.put(BeaconJsonView.ROOT_OBJECT_KEY, switchIds);
       return view;
    }

    /**
     * @param packageAdmin the packageAdmin to set
     */
    @Autowired
    public void setPackageAdmin(PackageAdmin packageAdmin) {
        this.packageAdmin = packageAdmin;
    }
    
    @RequestMapping("/controller/bundle/json")
    public View getOsgiJson(Map<String,Object>model) {
        BeaconJsonView view = new BeaconJsonView();
        List<Map<String, String>> bundleNames = new ArrayList<Map<String, String>>();
        for (int i = 0; i < this.bundleContext.getBundles().length; i++) {
            Bundle b = this.bundleContext.getBundles()[i];
            Map<String, String> m = new HashMap<String, String>();
            m.put("name", b.getSymbolicName());
            m.put("id", new Long(b.getBundleId()).toString());
            m.put("state", new Integer(b.getState()).toString());
            bundleNames.add(m);
        }
        model.put(BeaconJsonView.ROOT_OBJECT_KEY, bundleNames);
        return view;
    }
    
    class GetConcurrentStatsThread extends Thread {
        private List<OFStatistics> switchReply;
        private long switchId;
        private OFStatisticsType statType;
        private REQUESTTYPE requestType;
        private OFFeaturesReply featuresReply;
        private Map<MacVlanPair, Short> switchTable;
        
        public GetConcurrentStatsThread(long switchId, REQUESTTYPE requestType, OFStatisticsType statType) {
            this.switchId = switchId;
            this.requestType = requestType;
            this.statType = statType;
            this.switchReply = null;
            this.featuresReply = null;
            this.switchTable = null;
        }
        
        public List<OFStatistics> getStatisticsReply() {
            return switchReply;
        }
        
        public OFFeaturesReply getFeaturesReply() {
            return featuresReply;
        }
        
        public Map<MacVlanPair, Short> getSwitchTable() {
            return switchTable;
        }
        
        public long getSwitchId() {
            return switchId;
        }
        
        public void run() {
            if ((requestType == REQUESTTYPE.OFSTATS) && (statType != null)) {
                switchReply = getSwitchStatistics(switchId, statType);
            } else if (requestType == REQUESTTYPE.OFFEATURES) {
                featuresReply = beaconProvider.getSwitches().get(switchId).getFeaturesReply();
            } else if (requestType == REQUESTTYPE.SWITCHTABLE) {
                switchTable = beaconProvider.getSwitches().get(switchId).getMacVlanToPortMap();
            }
        }
    }
    
    @RequestMapping("/counter/{counterName}")
    public String getCounter(Map<String, Object> model, @PathVariable String counterName) {
      OneColumnLayout layout = new OneColumnLayout();
      try {
        counterName = URLDecoder.decode(counterName, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        //Just leave counterTitle undecoded if there is an issue - fail silently
      }
      model.put("title", "Counter: " + counterName);
      model.put("layout", layout);

      List<String> columnNames = Arrays.asList("Date", "Count");
      List<List<String>> cells = new ArrayList<List<String>>();
      ICounter counter = this.counterStore.getCounter(counterName);
      if (counter != null) {
          List<String> row = Arrays.asList(counter.getCounterDate().toString(), 
                  "" + counter.getCounterValue().getLong());
          cells.add(row);
//      } else {
//          samplingDate = new Date(0);
//          v = new CounterValue(CounterValue.CounterType.LONG);
      }
            
      layout.addSection(new TableSection("Counter " + counterName, columnNames, cells), null);
      
      return BeaconViewResolver.SIMPLE_VIEW;
    }

    @SuppressWarnings("unchecked")
    @RequestMapping("/counter/{counterTitle}/json")
    public View getCounterJson(Map<String, Object> model, @PathVariable String counterTitle, 
                                                             @RequestParam(required=false) String format) {        
        Date samplingDate;
        CounterValue v;
        ICounter counter = this.counterStore.getCounter(counterTitle);
        if (counter != null) {
            samplingDate = counter.getCounterDate();
            v = counter.getCounterValue();
        } else {
            samplingDate = new Date(0);
            v = new CounterValue(CounterValue.CounterType.LONG);
        }
        
        if (CounterValue.CounterType.LONG == v.getType()) {
            model.put(samplingDate.toString(), v.getLong());
        } else if (v.getType() == CounterValue.CounterType.DOUBLE) {
            model.put(samplingDate.toString(), v.getDouble());
        }

        return new BeaconJsonView();
        
    }
    
    protected String getOneSwitchCounter(List<List<String>> cells, String switchID, String counterName) {
        String fullCounterName = "";      
        
        try {
            counterName = URLDecoder.decode(counterName, "UTF-8");
            fullCounterName = switchID + ICounterStoreProvider.TitleDelimitor + counterName;
        } catch (UnsupportedEncodingException e) {
            //Just leave counterTitle undecoded if there is an issue - fail silently
        }


        ICounter counter = this.counterStore.getCounter(fullCounterName);
        if (counter != null) {
            List<String> row = Arrays.asList(switchID, counter.getCounterDate().toString(), 
                      "" + counter.getCounterValue().getLong());
            cells.add(row);
//      } else {
//          samplingDate = new Date(0);
//          v = new CounterValue(CounterValue.CounterType.LONG);
        }
        
        return fullCounterName;
    }
    
    @RequestMapping("/counter/{switchID}/{counterName}")
    public String switchCounter(Map<String, Object> model, @PathVariable String switchID, 
                                                           @PathVariable String counterName) {
      String fullCounterName = "";      
      TwoColumnLayout layout = new TwoColumnLayout();

      model.put("title", "Counter: " + fullCounterName);
      model.put("layout", layout);
      
      List<String> columnNames = Arrays.asList("SwitchID", "Date", "Count");
      List<List<String>> cells = new ArrayList<List<String>>();
      
      Long[] switchDpids;
      if (switchID.equals("all")) {
          switchDpids = beaconProvider.getSwitches().keySet().toArray(new Long[0]);
          for (Long dpid : switchDpids) {
              switchID = HexString.toHexString(dpid);

              fullCounterName = getOneSwitchCounter(cells, switchID, counterName);
          }
      } else {
          fullCounterName = getOneSwitchCounter(cells, switchID, counterName);
      }
                 
      layout.addSection(new TableSection("Counter " + fullCounterName, columnNames, cells), null);
      
      return BeaconViewResolver.SIMPLE_VIEW;
    }
    
    protected void getOneSwitchCounterJson(Map<String, Object> model, String switchID, String counterName) {
        String fullCounterName = "";      
        
        try {
            counterName = URLDecoder.decode(counterName, "UTF-8");
            fullCounterName = switchID + ICounterStoreProvider.TitleDelimitor + counterName;
        } catch (UnsupportedEncodingException e) {
            //Just leave counterTitle undecoded if there is an issue - fail silently
        }


        ICounter counter = this.counterStore.getCounter(fullCounterName);
        Map<String, Long> sample = new HashMap<String, Long> ();
        if (counter != null) {
            sample.put(counter.getCounterDate().toString(), counter.getCounterValue().getLong());
            model.put(switchID, sample);
//      } else {
//          samplingDate = new Date(0);
//          v = new CounterValue(CounterValue.CounterType.LONG);
        }
    }
    
    @RequestMapping("/counter/{switchID}/{counterName}/json")
    public View switchCounterJson(Map<String, Object> model, @PathVariable String switchID, 
                                                             @PathVariable String counterName,
                                                             @RequestParam(required=false) String format) {        
        
        Long[] switchDpids;
        if (switchID.equalsIgnoreCase("all")) {
            switchDpids = beaconProvider.getSwitches().keySet().toArray(new Long[0]);
            for (Long dpid : switchDpids) {
                switchID = HexString.toHexString(dpid);

                getOneSwitchCounterJson(model, switchID, counterName);
            }
        } else {
            getOneSwitchCounterJson(model, switchID, counterName);
        }

        return new BeaconJsonView();
        
    }

    
    @RequestMapping("/counter/{switchID}/{portID}/{counterName}")
    public String portCounter(Map<String, Object> model, @PathVariable String switchID, 
                                                         @PathVariable String portID,
                                                         @PathVariable String counterName) {
      String fullCounterName = "";
      try {
        switchID = URLDecoder.decode(switchID, "UTF-8");
        portID = URLDecoder.decode(portID, "UTF-8");
        counterName = URLDecoder.decode(counterName, "UTF-8");
        fullCounterName = switchID + ICounterStoreProvider.TitleDelimitor +
                          portID + ICounterStoreProvider.TitleDelimitor + 
                          counterName;
      } catch (UnsupportedEncodingException e) {
        //Just leave counterTitle undecoded if there is an issue - fail silently
      }

      return getCounter(model, fullCounterName);
    }
    
    @RequestMapping("/counter/{switchID}/{portID}/{counterName}/json")
    public View portCounterJson(Map<String, Object> model, @PathVariable String switchID,
                                                           @PathVariable String portID,
                                                           @PathVariable String counterName,
                                                           @RequestParam(required=false) String format) {        
        String fullCounterName = "";
        try {
          switchID = URLDecoder.decode(switchID, "UTF-8");
          portID = URLDecoder.decode(portID, "UTF-8");
          counterName = URLDecoder.decode(counterName, "UTF-8");
          fullCounterName = switchID + ICounterStoreProvider.TitleDelimitor +
                            portID + ICounterStoreProvider.TitleDelimitor + 
                            counterName;
        } catch (UnsupportedEncodingException e) {
          //Just leave counterTitle undecoded if there is an issue - fail silently
        }

        return getCounterJson(model, fullCounterName, format);
        
    }
    
    
    @RequestMapping("/counter/categories/{counterName}/{layer}")
    public String getCounterSubCategories(Map<String, Object> model, @PathVariable String counterName, 
            @PathVariable String layer) {
      OneColumnLayout layout = new OneColumnLayout();
      try {
        counterName = URLDecoder.decode(counterName, "UTF-8");
        layer = URLDecoder.decode(layer, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        //Just leave counterTitle undecoded if there is an issue - fail silently
      }
      model.put("title", "Type: " + counterName + " L" + layer);
      model.put("layout", layout);

      NetworkLayer nl = NetworkLayer.L2;
      if (layer.compareToIgnoreCase("3") == 0) {
          nl = NetworkLayer.L3;
      }
      List<String> columnNames = Arrays.asList("Type", "Categories");
      List<List<String>> cells = new ArrayList<List<String>>();
      List<String> categories = this.counterStore.getAllCategories(counterName, nl);
      if (categories != null) {
          List<String> row = Arrays.asList(counterName, categories.toString());
          cells.add(row);
      }
            
      layout.addSection(new TableSection("Type: " + counterName + " L" + layer, columnNames, cells), null);
      
      return BeaconViewResolver.SIMPLE_VIEW;
    }

    @SuppressWarnings("unchecked")
    @RequestMapping("/counter/categories/{counterTitle}/{layer}/json")
    public View getCounterSubCategoriesJson(Map<String, Object> model, @PathVariable String counterTitle,
            @PathVariable String layer, @RequestParam(required=false) String format) {
        NetworkLayer nl = NetworkLayer.L2;
        if (layer.compareToIgnoreCase("3") == 0) {
            nl = NetworkLayer.L3;
        }
        List<String> categories = this.counterStore.getAllCategories(counterTitle, nl);
        if (categories != null) {
            model.put(counterTitle + "." + layer, categories);
            //log.info("Switch L2 Counter, " + counterTitle + " has " + categories.toString());
        }

        //log.info("Switch L2 Counter, " + counterTitle + " has no sub-categories");
        
        return new BeaconJsonView();
    }
    
    protected String getOneSwitchCounterCategories(List<List<String>> cells, String switchID, String counterName, String layer) {
        String fullCounterName = "";      
        
        try {
            counterName = URLDecoder.decode(counterName, "UTF-8");
            layer = URLDecoder.decode(layer, "UTF-8");
            fullCounterName = switchID + ICounterStoreProvider.TitleDelimitor + counterName;
        } catch (UnsupportedEncodingException e) {
            //Just leave counterTitle undecoded if there is an issue - fail silently
        }

        NetworkLayer nl = NetworkLayer.L2;
        if (layer.compareToIgnoreCase("3") == 0) {
            nl = NetworkLayer.L3;
        }

        List<String> categories = this.counterStore.getAllCategories(fullCounterName, nl);
        if (categories != null) {
            List<String> row = Arrays.asList(switchID, categories.toString());
            cells.add(row);
            
            //log.info("Switch L2 Counter, " + fullCounterName + " has " + categories.toString());
        }
        //log.info("Switch L2 Counter, " + fullCounterName + " has no sub-categories");
        
        return fullCounterName;
    }
    
    @RequestMapping("/counter/categories/{switchID}/{counterName}/{layer}")
    public String getSwitchCounterSubCategories(Map<String, Object> model, @PathVariable String switchID, 
                                                             @PathVariable String counterName,
                                                             @PathVariable String layer) {
        String fullCounterName = "";
        TwoColumnLayout layout = new TwoColumnLayout();

        model.put("title", "Type:" + counterName + " L" + layer);
        model.put("layout", layout);
        
        List<String> columnNames = Arrays.asList("SwitchID", "Categories");
        List<List<String>> cells = new ArrayList<List<String>>();
        
        Long[] switchDpids;
        if (switchID.equals("all")) {
            switchDpids = beaconProvider.getSwitches().keySet().toArray(new Long[0]);
            for (Long dpid : switchDpids) {
                switchID = HexString.toHexString(dpid);

                fullCounterName = getOneSwitchCounterCategories(cells, switchID, counterName, layer);
            }
        } else {
            fullCounterName = getOneSwitchCounterCategories(cells, switchID, counterName, layer);
        }
                   
        layout.addSection(new TableSection("Counter " + fullCounterName, columnNames, cells), null);
        
        return BeaconViewResolver.SIMPLE_VIEW;

    }
    
    protected void getOneSwitchCounterCategoriesJson(Map<String, Object> model, String switchID, String counterName, String layer) {
        String fullCounterName = "";      
        NetworkLayer nl = NetworkLayer.L2;
        
        try {
            counterName = URLDecoder.decode(counterName, "UTF-8");
            layer = URLDecoder.decode(layer, "UTF-8");
            fullCounterName = switchID + ICounterStoreProvider.TitleDelimitor + counterName;
        } catch (UnsupportedEncodingException e) {
            //Just leave counterTitle undecoded if there is an issue - fail silently
        }

        if (layer.compareToIgnoreCase("3") == 0) {
            nl = NetworkLayer.L3;
        }
        List<String> categories = this.counterStore.getAllCategories(fullCounterName, nl);
        if (categories != null) {
            model.put(fullCounterName + "." + layer, categories);
            //log.info("Switch L2 Counter, " + fullCounterName + " has " + categories.toString());
        }
    }
    
    @RequestMapping("/counter/categories/{switchID}/{counterName}/{layer}/json")
    public View getSwitchCounterSubCategoriesJson(Map<String, Object> model, @PathVariable String switchID, 
                                                             @PathVariable String counterName,
                                                             @PathVariable String layer,
                                                             @RequestParam(required=false) String format) {
        Long[] switchDpids;
        if (switchID.equalsIgnoreCase("all")) {
            switchDpids = beaconProvider.getSwitches().keySet().toArray(new Long[0]);
            for (Long dpid : switchDpids) {
                switchID = HexString.toHexString(dpid);

                getOneSwitchCounterCategoriesJson(model, switchID, counterName, layer);
            }
        } else {
            getOneSwitchCounterCategoriesJson(model, switchID, counterName, layer);
        }
        
        return new BeaconJsonView();
    }
    
     
    @RequestMapping("/counter/categories/{switchID}/{portID}/{counterName}/{layer}")
    public String portCounterSubCategories(Map<String, Object> model, @PathVariable String switchID, 
                                                         @PathVariable String portID,
                                                         @PathVariable String counterName,
                                                         @PathVariable String layer) {
      String fullCounterName = "";
      try {
        switchID = URLDecoder.decode(switchID, "UTF-8");
        portID = URLDecoder.decode(portID, "UTF-8");
        counterName = URLDecoder.decode(counterName, "UTF-8");
        layer = URLDecoder.decode(layer, "UTF-8");
        fullCounterName = switchID + ICounterStoreProvider.TitleDelimitor +
                          portID + ICounterStoreProvider.TitleDelimitor + 
                          counterName;
      } catch (UnsupportedEncodingException e) {
        //Just leave parameter undecoded if there is an issue - fail silently
      }

      return getCounterSubCategories(model, fullCounterName, layer);
    }
    
    @RequestMapping("/counter/categories/{switchID}/{portID}/{counterName}/{layer}/json")
    public View portCounterSubCategoriesJson(Map<String, Object> model, @PathVariable String switchID,
                                                           @PathVariable String portID,
                                                           @PathVariable String counterName,
                                                           @PathVariable String layer,
                                                           @RequestParam(required=false) String format) {        
        String fullCounterName = "";
        try {
          switchID = URLDecoder.decode(switchID, "UTF-8");
          portID = URLDecoder.decode(portID, "UTF-8");
          counterName = URLDecoder.decode(counterName, "UTF-8");
          layer = URLDecoder.decode(layer, "UTF-8");
          fullCounterName = switchID + ICounterStoreProvider.TitleDelimitor +
                            portID + ICounterStoreProvider.TitleDelimitor + 
                            counterName;
        } catch (UnsupportedEncodingException e) {
          //Just leave counterTitle undecoded if there is an issue - fail silently
        }

        return getCounterSubCategoriesJson(model, fullCounterName, layer, format);
        
    }
}
