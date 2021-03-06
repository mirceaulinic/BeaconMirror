package net.beaconcontroller.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.beaconcontroller.web.view.BeaconJsonView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.View;

/**
 * 
 * @author Kyle Forster (kyle.forster@bigswitch.com)
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */

@Controller
public class WebManageableController {
    protected static Logger log = LoggerFactory.getLogger(WebManageableController.class);

    protected List<IWebManageable> webManageables = new ArrayList<IWebManageable>();

    @RequestMapping(value = "wm")
    public View overview(Map<String, Object> model) {
        BeaconJsonView view = new BeaconJsonView();
        view.setDisableCaching(true);
        Collections.sort(webManageables, new Comparator<IWebManageable>(){
            public int compare(IWebManageable w1, IWebManageable w2) {
                    return w1.getName().compareToIgnoreCase(w2.getName());
            }
        });
        model.put(BeaconJsonView.ROOT_OBJECT_KEY, webManageables);
        return view;
    }

    /**
     * @param webManageables the webManageables to set
     */
    @Autowired
    public void setWebManageables(List<IWebManageable> webManageables) {
        this.webManageables = webManageables;
    }
}
