// BLE.js
class BLEController {
    constructor() {
        this.device = null;
        this.server = null;
        this.service = null;

        // Callbacks
        this.onControlChange = null;
        this.onData = null;      // raw DataView
        this.onTemp = null;      // raw temperature DataView
        this.onScanFail = null;
        this.onDisconnect = null;
        this.onConnected = null;
        this.onConnectFail = null;

        this.gatt_busy = false; // to prevent multiple simultaneous operations
        
        console.log('🔧 BLEController initialized');
    }

    // Request device and connect
    async startScanning() {
        console.log('🔍 Starting BLE scan...');
        try {
            this.device = await navigator.bluetooth.requestDevice({
                filters: [
                    { manufacturerData: [{
                        companyIdentifier: 0xFFFF,
                        dataPrefix: new Uint8Array([0x53, 0x41, 0x4C, 0x30]) // SAL0
                    }] },
                    { manufacturerData: [{
                        companyIdentifier: 0xFFFF,
                        dataPrefix: new Uint8Array([0x53, 0x41, 0x4C, 0x31]) // SAL1
                    }] }
                ],
                optionalServices: [UUIDS.service],
            });
            console.log('📱 Device selected:', this.device.name);
            this.device.addEventListener("gattserverdisconnected", () => {
                console.log('🔌 GATT server disconnected');
                if (this.onDisconnect) this.onDisconnect();
            });
            await this.connect();

        } catch (err) {
            console.error('❌ Scan failed:', err);
            if (this.onScanFail) this.onScanFail(err);
        }
    }

    async connect() {
        console.log('📡 Connecting to GATT server...');
        try {
            this.server = await this.device.gatt.connect();
            console.log('✅ GATT server connected');
            this.service = await this.server.getPrimaryService(UUIDS.service);
            console.log('✅ Primary service obtained');
            await this.#initCharacteristics();
            console.log('✅ Characteristics initialized');
            if (this.onConnected) this.onConnected(this.device.name || "(Unnamed device)");
        } catch (err) {
            console.error('❌ Connection failed:', err);
            if (this.onConnectFail) this.onConnectFail(this.device.name || "(Unnamed device)", err);
        }
    }

    async #initCharacteristics() {
        console.log('🔧 Initializing characteristics...');
        
        // --- Control ---
        this.controlChar = await this.service.getCharacteristic(UUIDS.control);
        await this.controlChar.startNotifications();
        console.log('✅ Control characteristic notifications enabled');
        this.controlChar.addEventListener("characteristicvaluechanged", e => {
            console.log('📝 Control data changed:', new Uint8Array(e.target.value.buffer));
            if (this.onControlChange) this.onControlChange(e.target.value);
        });

        // --- Data ---
        const dataChar = await this.service.getCharacteristic(UUIDS.data);
        await dataChar.startNotifications();
        console.log('✅ Data characteristic notifications enabled');
        dataChar.addEventListener("characteristicvaluechanged", e => {
            console.log('💓 Vital data received:', e.target.value.byteLength, 'bytes');
            if (this.onData) this.onData(e.target.value);
        });

        // --- Temperature ---
        const tempChar = await this.service.getCharacteristic(UUIDS.temperature);
        await tempChar.startNotifications();
        console.log('✅ Temperature characteristic notifications enabled');
        tempChar.addEventListener("characteristicvaluechanged", e => {
            const raw = (e.target.value.getUint8(1) << 8) | e.target.value.getUint8(0);
            const tempC = raw / 100.0;
            console.log('🌡️ Temperature:', tempC.toFixed(2), '°C');
            if (this.onTemp) this.onTemp(e.target.value);
        });        
    }

    async writePasskey(passkey) {
        if (!this.controlChar) return;
        while (this.gatt_busy) await new Promise(r => setTimeout(r, 10));   
        this.gatt_busy = true;
        const keyBytes = new TextEncoder().encode(passkey);
        const prefixed = new Uint8Array(keyBytes.length + 1);
        prefixed[0] = 'k'.charCodeAt(0);  // first byte = 'k'
        prefixed.set(keyBytes, 1);        // copy original passkey after it
        await this.controlChar.writeValue(prefixed);
        this.gatt_busy = false;
        console.log("🔑 Sent Passkey:", passkey);
        
    }
    
    async writeCommand(cmd) {
        if (!this.controlChar) return;
        while (this.gatt_busy) await new Promise(r => setTimeout(r, 10));
        this.gatt_busy = true;
        const buffer = new Uint8Array(2);
        buffer[0] = 'c'.charCodeAt(0); // prefix
        buffer[1] = cmd.charCodeAt(0);               // numeric command
        await this.controlChar.writeValue(buffer);
        this.gatt_busy = false;
        console.log("⚙️ Sent Command:", cmd, "Buffer:", Array.from(buffer));
    }

    async writeSettings(settings) {
        if (!this.controlChar) return;
        while (this.gatt_busy) await new Promise(r => setTimeout(r, 10));
        this.gatt_busy = true;
        let prefixed = new Uint8Array(settings.length + 1);
        prefixed[0] = 't'.charCodeAt(0);     // first byte = 't'
        prefixed.set(settings, 1);           // copy original settings after it
        await this.controlChar.writeValue(prefixed);
        this.gatt_busy = false;
        console.log("⚙️ Sent Settings:", settings.length, "bytes", Array.from(settings).slice(0, 20), '...');
    }

    async disconnect() {
        console.log('🔌 Disconnecting from device...');
        if (this.device?.gatt?.connected) await this.device.gatt.disconnect();
        this.device = null;
        this.server = null;
        this.service = null;
        console.log('✅ Disconnected successfully');
    }
}
