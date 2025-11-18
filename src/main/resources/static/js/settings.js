/************* Schema (order + types + per-field config) *************/
const SECRET_MASK = "********";

/************* Tooltips: per-setting help text *************/
const TOOLTIP_TEXT = {
    // DEX — Pricing
    dexAskMultiplier: "Multiplier applied to the CEX ask when quoting the DEX ask (SELL). Setting 1.01 is 1%",
    dexBidMultiplier: "Multiplier applied to the CEX bid when quoting the DEX bid (BUY). Setting 0.99 is 1%",
    dexPriceMoveThreshold: "The threshold for updating the DEX order when the price moves on the central exchange.",

    // DEX — Volumes
    dexMinimumTokenSellVolume: "Minimum fUSD notional to place for sell orders on the DEX (otherwise based on volume on central exchange).",
    dexMinimumTokenBuyVolume: "Minimum fUSD notional to place for buy order on the DEX  (otherwise based on volume on central exchange).",
    dexMaximumTokenSellVolume: "Maximum fUSD notional allowed per sell order on DEX (empty = no cap).",
    dexMaximumTokenBuyVolume: "Maximum fUSD notional allowed per buy order on DEX (empty = no cap).",

    // Core Toggles
    zanoSellEnabled: "After a DEX SELL, allow selling ZANO on CEX to rebalance.",
    fusdBuyEnabled: "After a DEX SELL, allow buying fUSD for USDT on CEX to rebalance.",
    zanoBuyEnabled: "After a DEX BUY, allow buying ZANO on CEX to rebalance.",
    fusdSellEnabled: "After a DEX BUY, allow selling fUSD for USDT on CEX to rebalance.",
    zanoMoveFromWalletEnabled: "Permit moving ZANO out of the wallet to the CEX account.",
    mexcFusdWithdrawEnabled: "Permit fUSD withdrawals from the CEX account to the DEX trader.",
    autoStartDexTradeBot: "Automatically start the DEX trade bot on app launch.",

    // Transfers / Auto-move
    zanoMoveToCexThreshold: "If wallet ZANO exceeds this amount, auto-move to CEX.",
    zanoMoveToCexMinTransfer: "Minimum ZANO size for an auto-move transfer to CEX.",
    fusdMoveToWalletTheshold: "If CEX fUSD exceeds this amount, auto-move to wallet.",
    fusdMoveToWalletMinTransfer: "Minimum fUSD size for an auto-move transfer to wallet.",

    // MEXC API / Deposit Addresses
    mexcApiKey: "Your MEXC API key used for authenticated CEX requests.",
    mexcApiSecret: "Your MEXC API secret (kept masked; leave blank to keep existing).",

    // CEX — Selling Parameters
    zanoSellPercent: "Percent of available ZANO to sell on CEX during a rebalance. Setting 100 means 100% (recommended)",
    zanoSellPriceMultiplier: "Multiplier applied to reference price when placing the CEX sell. Setting 1.01 is 1%."
};

const SETTINGS_SCHEMA = [
    // DEX pricing
    { section: "DEX — Pricing" },
    { key:"dexAskMultiplier", label:"DEX ask multiplier", type:"number", step:"0.0001" },
    { key:"dexBidMultiplier", label:"DEX bid multiplier", type:"number", step:"0.0001" },
    { key:"dexPriceMoveThreshold", label:"DEX price move threshold (%)", type:"number", step:"0.01" },

    // DEX volumes
    { section: "DEX — Volumes" },
    { key:"dexMinimumTokenSellVolume", label:"Minimum token sell volume (fUSD)", type:"number", step:"1" },
    { key:"dexMinimumTokenBuyVolume",  label:"Minimum token buy volume (fUSD)",  type:"number", step:"1" },
    { key:"dexMaximumTokenSellVolume", label:"Maximum token sell volume (fUSD)", type:"number", step:"1", nullable:true },
    { key:"dexMaximumTokenBuyVolume",  label:"Maximum token buy volume (fUSD)",  type:"number", step:"1", nullable:true },

    // Core toggles
    { section: "Core Toggles" },
    { key:"zanoSellEnabled",            label:"Enable ZANO sell on CEX after SELL",        type:"boolean" },
    { key:"fusdBuyEnabled",             label:"Enable fUSD buy on CEX after SELL",  type:"boolean" },
    { key:"zanoBuyEnabled",            label:"Enable ZANO buy on CEX after BUY",        type:"boolean" },
    { key:"fusdSellEnabled",             label:"Enable fUSD sell on CEX after BUY",  type:"boolean" },
    { key:"zanoMoveFromWalletEnabled",  label:"Allow moving ZANO from wallet",  type:"boolean" },
    { key:"mexcFusdWithdrawEnabled",    label:"Enable fUSD withdrawals on CEX", type:"boolean" },
    { key:"autoStartDexTradeBot",       label:"Enable autostart of DEX trade bot", type:"boolean" },

    // Transfers
    { section: "Transfers / Auto-move" },
    { key:"zanoMoveToCexThreshold",     label:"ZANO move-to-CEX threshold",     type:"number", step:"0.00000001" },
    { key:"zanoMoveToCexMinTransfer",   label:"ZANO min transfer size",         type:"number", step:"0.00000001" },
    { key:"fusdMoveToWalletTheshold",   label:"fUSD move-to-wallet threshold",  type:"number", step:"1" },
    { key:"fusdMoveToWalletMinTransfer",label:"fUSD min transfer size",         type:"number", step:"1", nullable:true },

    // API & addresses
    { section: "MEXC API / Deposit Addresses" },
    { key:"mexcApiKey",               label:"MEXC API Key",                         type:"text",  autocomplete:"off" },
    { key:"mexcApiSecret",            label:"MEXC API Secret",                      type:"password", autocomplete:"new-password", maskable:true },
    { key:"telegramBotToken",         label:"Telegram bot token",             type:"text",  autocomplete:"off" },
    // CEX selling params (kept near API for convenience)
    { section: "CEX — Selling Parameters" },
    { key:"zanoSellPercent",          label:"ZANO sell percent (%)",                type:"number", step:"0.01" },
    { key:"zanoSellPriceMultiplier",  label:"ZANO sell price multiplier",           type:"number", step:"0.0001" }
];

/************* DOM helpers *************/
function sectionRow(title){
    return $("<tr>").addClass("table-section-row").append($("<td>").attr("colspan",2).text(title));
}
function fieldId(key){ return "setting-" + key.replace(/[^A-Za-z0-9_-]/g,"-"); }

function initTooltips(){
    const els = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    els.forEach(el => new bootstrap.Tooltip(el, { container: 'body' }));
}

function renderSettingsTable(){
    const $tb = $("#settings-tbody").empty();
    SETTINGS_SCHEMA.forEach(def=>{
        if(def.section){
            $tb.append(sectionRow(def.section));
            return;
        }
        const id = fieldId(def.key);
        const $tr = $("<tr>");
        // Label + help
        const $label = $("<label>").attr("for", id).addClass("mb-0").text(def.label || def.key);

        // Small “?” button with tooltip (right of label)
        const tipText = TOOLTIP_TEXT[def.key] || "No description available yet.";
        const $helpBtn = $("<button>")
            .attr({ type: "button", "aria-label": "Help" })
            .addClass("btn btn-outline-secondary btn-sm ms-2 p-0 lh-1 setting-help")
            .html('<i class="bi bi-question-circle"></i>')
            .attr({
                "data-bs-toggle": "tooltip",
                "data-bs-placement": "right",
                "title": tipText
            });

        const $left = $("<td>").append($label, $helpBtn);
        const $right = $("<td>");

        if(def.type === "boolean"){
            const $wrap = $("<div>").addClass("form-check form-switch");
            const $input = $("<input>").addClass("form-check-input app-setting")
                .attr({type:"checkbox", id, name:def.key, "data-type":"boolean"});
            $wrap.append($input);
            $right.append($wrap);
        }else{
            const $group = $("<div>").addClass("input-group");
            const $input = $("<input>").addClass("form-control app-setting")
                .attr({
                    type: def.type === "password" ? "password" : (def.type || "text"),
                    id, name:def.key, placeholder: (def.label || def.key),
                    autocapitalize:"off", autocorrect:"off", spellcheck:"false",
                    "data-type": def.type || "text",
                    "data-nullable": def.nullable ? "1" : "0"
                });
            if(def.step) $input.attr("step", def.step);
            if(def.autocomplete) $input.attr("autocomplete", def.autocomplete);

            if(def.maskable){
                const $btn = $("<button>").attr({type:"button"}).addClass("btn btn-outline-secondary")
                    .html('<i class="bi bi-eye-slash"></i>');
                $btn.on("click", function(){
                    const t = $input.attr("type")==="password" ? "text" : "password";
                    $input.attr("type", t);
                    $(this).find("i").toggleClass("bi-eye-slash bi-eye");
                });
                $group.append($input, $btn);
            }else{
                $group.append($input);
            }
            $right.append($group);
        }

        $tr.append($left, $right);
        $tb.append($tr);
    });
    initTooltips();
}

/************* Data binding *************/
let originalSettings = null;

function applySettings(data){
    originalSettings = JSON.parse(JSON.stringify(data || {})); // deep copy
    // Fill values
    SETTINGS_SCHEMA.forEach(def=>{
        if(def.section) return;
        const key = def.key, id = "#"+fieldId(key);
        const val = data ? data[key] : undefined;

        if(def.type === "boolean"){
            $(id).prop("checked", !!val);
        }else if(def.type === "number"){
            // null shows as empty
            if(val === null || typeof val === "undefined") $(id).val("");
            else $(id).val(String(val));
        }else{
            if(key === "mexcApiSecret"){
                $(id).val(val ? SECRET_MASK : "");
            }else{
                $(id).val(typeof val === "undefined" || val === null ? "" : String(val));
            }
        }
    });

    setDirty(false);
    $("#settings-status").text("Loaded");
}

function readSettingsFromForm(){
    const out = {};
    SETTINGS_SCHEMA.forEach(def=>{
        if(def.section) return;
        const key = def.key, $el = $("#"+fieldId(key));
        const dtype = $el.data("type");

        if(dtype === "boolean"){
            out[key] = $el.is(":checked");
        }else if(dtype === "number"){
            const raw = ($el.val() || "").trim();
            if(raw === ""){
                out[key] = (def.nullable ? null : null); // treat empty as null
            }else{
                const num = Number(raw);
                out[key] = isFinite(num) ? num : null;
            }
        }else{
            let txt = ($el.val() || "").toString();
            if(key === "mexcApiSecret"){
                // Do not overwrite if left masked or blank
                if(txt === "" || txt === SECRET_MASK){
                    // omit the key; backend keeps previous secret
                    return;
                }
            }
            out[key] = txt;
        }
    });
    return out;
}

function isDirty(){
    if(!originalSettings) return false;
    const current = readSettingsFromForm();
    // Compare against original, but note: current may omit mexcApiSecret; so build a merged view
    const merged = {...originalSettings, ...current};
    // We only consider schema keys
    for(const def of SETTINGS_SCHEMA){
        if(def.section) continue;
        const k = def.key;
        const a = originalSettings[k];
        const b = merged[k];
        if(def.type === "number"){
            // Treat null vs empty the same
            if( (a ?? null) !== (b ?? null) ) return true;
        }else{
            if(String(a ?? "") !== String(b ?? "")) return true;
        }
    }
    return false;
}

function setDirty(flag){
    $("#unsaved-pill").toggleClass("d-none", !flag);
}

/************* API *************/
function fetchAppSettings(){
    $("#settings-status").text("Loading…");
    return $.getJSON("/api/app-settings")
        .done(function(res){
            const payload = res && res.payload ? res.payload : {};
            applySettings(payload);
        })
        .fail(function(jq){
            $("#settings-status").text("Failed ("+jq.status+")");
            showSaveAlert("danger", "Failed to load /api/app-settings ("+jq.status+").");
        });
}

function saveAppSettings(){
    const data = readSettingsFromForm();

    $("#btn-save").prop("disabled", true);
    $("#settings-status").text("Saving…");

    return $.ajax({
        url: "/api/app-settings",
        method: "POST",
        contentType: "application/json",
        data: JSON.stringify(data) // post the same keys (top-level)
    })
        .done(function(){
            showSaveAlert("success", "Settings saved.");
            // Re-fetch to get normalized values (and to refresh masked secret etc.)
            return fetchAppSettings();
        })
        .fail(function(jq){
            showSaveAlert("danger", "Failed to save settings ("+jq.status+").");
        })
        .always(function(){
            $("#btn-save").prop("disabled", false);
            $("#settings-status").text("Ready");
            setDirty(false);
        });
}

function showSaveAlert(kind, msg){
    const $a = $("#save-alert");
    $a.removeClass("d-none").removeClass("alert-success alert-danger alert-warning")
        .addClass("alert-"+(kind || "success")).text(msg);
    setTimeout(()=> $a.addClass("d-none"), 4000);
}

/************* Init *************/
$(function(){
    renderSettingsTable();
    fetchAppSettings();

    // Dirty tracking
    $("#settings-tbody").on("input change", ".app-setting", function(){
        setDirty(isDirty());
    });

    $("#btn-refresh").on("click", function(){
        fetchAppSettings();
    });

    $("#app-settings-form").on("submit", function(e){
        e.preventDefault();
        saveAppSettings();
    });
});