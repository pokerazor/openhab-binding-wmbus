package de.unidue.stud.sehawagn.openhab.binding.wmbus.internal.discovery;

import static de.unidue.stud.sehawagn.openhab.binding.wmbus.WMBusBindingConstants.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.openmuc.jmbus.wireless.WMBusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import de.unidue.stud.sehawagn.openhab.binding.wmbus.handler.WMBusBridgeHandler;
import de.unidue.stud.sehawagn.openhab.binding.wmbus.handler.WMBusMessageListener;
import de.unidue.stud.sehawagn.openhab.binding.wmbus.handler.WMBusTechemHKVHandler;

public class WMBusHKVDiscoveryService extends AbstractDiscoveryService implements WMBusMessageListener {

    private final Logger logger = LoggerFactory.getLogger(WMBusHKVDiscoveryService.class);

    private final static int SEARCH_TIME = 480; // wait some time, because the interval of all HCAs may be long

    // @formatter:off
    //TODO diese Liste erweitern um Kamstrup und Qundis-Geräte
    private final static Map<String, String> TYPE_TO_WMBUS_ID_MAP = new ImmutableMap.Builder<String, String>()
            .put("68TCH105255", "techem_hkv").build();
    // @formatter:on

    private WMBusBridgeHandler bridgeHandler;

    public WMBusHKVDiscoveryService(WMBusBridgeHandler bridgeHandler) {
        super(SEARCH_TIME);
        this.bridgeHandler = bridgeHandler;
    }

    public WMBusHKVDiscoveryService(Set<ThingTypeUID> supportedThingTypes, int timeout,
            boolean backgroundDiscoveryEnabledByDefault) throws IllegalArgumentException {
        super(supportedThingTypes, timeout, backgroundDiscoveryEnabledByDefault);
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypes() {
        logger.debug("discovery: getsupperted thing types: these are "
                + WMBusTechemHKVHandler.SUPPORTED_THING_TYPES.toString());
        return WMBusTechemHKVHandler.SUPPORTED_THING_TYPES;
    }

    @Override
    protected void startScan() {
        // do nothing since there is no active scan possible at the moment, only receiving
        logger.debug(
                "discovery: startScan(): unimplemented - devices will be added upon reception of telegrams from them");
    }

    @Override
    public void onNewWMBusDevice(WMBusMessage wmBusDevice) {
        logger.debug("discovery: onnewWMBusDevice(): new device " + wmBusDevice.getSecondaryAddress().toString());
        onWMBusMessageReceivedInternal(wmBusDevice);
    }

    private void onWMBusMessageReceivedInternal(WMBusMessage wmBusDevice) {
        logger.debug("discovery: msgreceivedInternal() begin");
        // try to find this device in the list of supported devices
        ThingUID thingUID = getThingUID(wmBusDevice);

        if (thingUID != null) {
            logger.debug("discorvey: msgreceivedInternal(): device is known");
            // device known -> create discovery result
            ThingUID bridgeUID = bridgeHandler.getThing().getUID();
            Map<String, Object> properties = new HashMap<>(1);
            properties.put(PROPERTY_HKV_ID, wmBusDevice.getSecondaryAddress().getDeviceId().toString());

            // TODO label according to uid
            DiscoveryResult discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                    .withRepresentationProperty(wmBusDevice.getSecondaryAddress().getDeviceId().toString())
                    .withBridge(bridgeUID)
                    .withLabel("WMBus device ser.no. " + wmBusDevice.getSecondaryAddress().getDeviceId()).build();
            logger.debug("discorvey: msgreceivedInternal(): notifying OpenHAB of new thing");
            thingDiscovered(discoveryResult);
        } else {
            // device unknown -> log message
            logger.debug("discovered unsupported WMBus device of type '{}' with secondary address {}",
                    wmBusDevice.getSecondaryAddress().getDeviceType(), wmBusDevice.getSecondaryAddress().toString());
        }
    }

    // checks if this device is of the supported kind -> if yes, will be discovered
    private ThingUID getThingUID(WMBusMessage wmBusDevice) {
        logger.debug("discover: getThingUID begin");
        ThingUID bridgeUID = bridgeHandler.getThing().getUID();
        ThingTypeUID thingTypeUID = getThingTypeUID(wmBusDevice);

        if (thingTypeUID != null && getSupportedThingTypes().contains(thingTypeUID)) {
            logger.debug("discover: getThingUID have bridgeUID " + bridgeUID.toString());
            logger.debug("discover: getThingUID have thingTypeUID " + thingTypeUID.toString());
            return new ThingUID(thingTypeUID, bridgeUID, wmBusDevice.getSecondaryAddress().getDeviceId() + "");
        } else {
            logger.debug("discover: get ThingUID found no supported device");
            return null;
        }
    }

    private ThingTypeUID getThingTypeUID(WMBusMessage wmBusDevice) {
        String typeIdString = wmBusDevice.getControlField() + "" + wmBusDevice.getSecondaryAddress().getManufacturerId()
                + "" + wmBusDevice.getSecondaryAddress().getVersion() + ""
                + wmBusDevice.getSecondaryAddress().getDeviceType().getId();
        logger.debug("discovery: getThingTypeUID(): This device has typeID " + typeIdString
                + " -- supported device types are "
                + Arrays.toString(WMBusHKVDiscoveryService.TYPE_TO_WMBUS_ID_MAP.keySet().toArray()));
        String thingTypeId = TYPE_TO_WMBUS_ID_MAP.get(typeIdString);
        return thingTypeId != null ? new ThingTypeUID(BINDING_ID, thingTypeId) : null;
    }

    public void activate() {
        logger.debug("discovery: activate: registering as wmbusmessagelistener");
        bridgeHandler.registerWMBusMessageListener(this);
    }

    @Override
    public void onChangedWMBusDevice(WMBusMessage wmBusDevice) {
        // nothing to do
    }

}
