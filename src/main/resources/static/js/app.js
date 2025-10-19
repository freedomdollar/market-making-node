/***********************
 * Utilities (de‑duped)
 ***********************/
/************* App Status helpers *************/
let mexcKeyState = null; // 'ok' | 'missing' | 'invalid' | null

function fmtNumber(n){ if(n===null||n===undefined||isNaN(n)) return "—"; try{ return Number(n).toLocaleString(); }catch{ return String(n);} }
function fmtFloat(n, max=8, min=0){ const x=Number(n); if(!isFinite(x)) return "—"; return x.toLocaleString(undefined,{maximumFractionDigits:max, minimumFractionDigits:min}); }
// Per your spec: percent = max_net_seen_height / height
function computePercentBySpec(height, maxSeen){ const h=Number(height)||0, m=Number(maxSeen)||0; if(h<=0) return 0; return (h/m)*100; }
function toBigIntVal(v){ try{ if(typeof v==="bigint") return v; if(typeof v==="number") return BigInt(Math.trunc(v)); if(typeof v==="string") return BigInt(v.trim()); }catch(_){} return 0n; }
function fmtAtomic(atomic, decimals){ try{ const d=Math.max(0, Number(decimals)||0); let v=toBigIntVal(atomic); const neg=v<0n; if(neg) v=-v; const pow=10n**BigInt(d); const i=v/pow, f=v%pow; let frac=f.toString().padStart(d,"0").replace(/0+$/,""); return (neg?"-":"")+i.toString()+(frac?("."+frac):""); }catch(_){ const d=Math.max(0, Number(decimals)||0); const num=Number(atomic)/Math.pow(10,d); return isFinite(num)? num.toLocaleString(undefined,{maximumFractionDigits:d}) : "—"; } }
function updateSyncTimestamp(){ $("#last-updated").text("Last updated: "+ new Date().toLocaleString()); }
function updateTradesTimestamp(){ $("#trades-last-updated").text("Last updated: "+ new Date().toLocaleString()); }
function fmtTime(ts) {
    if (!ts) return "—";
    try {
        return new Date(ts).toLocaleString();
    } catch {
        return "—";
    }
}

function updateTimestamp() {
    const now = new Date();
    document.getElementById("last-updated-buy").textContent = "Last updated: " + now.toLocaleString();
}

function setCardEnabled(cardId, enabled, message){
    const $c = $("#"+cardId);
    $c.toggleClass("is-disabled", !enabled);
    const $ov = $c.find(".card-overlay");
    if(!enabled){ $ov.text(message||"Service initializing…"); }
}

/***********************
 * Service readiness
 ***********************/
const serviceState = { appStarted:false, daemon:false, wallet:false, dex:false, cex:false, dexTradingActive:false };
let daemonModal;

function applyServiceState(){
    // Modal for daemon
    if(serviceState.daemon) {
        daemonModal?.hide();
        setCardEnabled("sync-card", true);
    } else {
        setCardEnabled("sync-card", false, "Zano daemon is initializing…");
        if (serviceState.app) {
            $("#daemon-modal-body").html("Zano daemon is initializing, please wait 5–20 minutes.");
        } else {
            $("#daemon-modal-body").html("Application initializing, please wait a few seconds");
        }
        daemonModal?.show();

    }

    // Card gates
    setCardEnabled("wallet-card", serviceState.wallet, "Wallet service not ready…");
    setCardEnabled("orders-card", serviceState.dex, "DEX trading service not ready…");
    setCardEnabled("metrics-card", serviceState.dex, "DEX trading service not ready…");
    setCardEnabled("mexc-card", serviceState.cex, "CEX trading service not ready…");

    // Start/stop pollers per service
    if(serviceState.daemon){ startPoller("zano", fetchZanoStatus, 10000); } else { stopPoller("zano"); }
    if(serviceState.wallet){
        startPoller("mm", fetchMmStatus, 5000);
    } else {
        stopPoller("mm");
    }
    if(serviceState.cex){ startPoller("mexc", fetchMexcStatus, 15000); } else { stopPoller("mexc"); }
    if(serviceState.dex){ startPoller("trades", fetchTrades, 15000); } else { stopPoller("trades"); }
    if(serviceState.dex){ startPoller("tradesbuy", fetchTradesBuy, 15000); } else { stopPoller("tradesbuy"); }

    if (serviceState.pendingAlias) {
        $("#alias-input").attr("disabled", "disabled");
        $("#alias-save-btn").attr("disabled", "disabled");
        $("#alias-alert").html("Alias is pending confirmations.");
    }

    if (serviceState.tradingOpen) {
        $("#btn-start-bot").prop("disabled", true);
        $("#btn-stop-bot").prop("disabled", false);
    } else {
        if (serviceState.tradingOpen) {
            $("#btn-start-bot").prop("disabled", false);
            $("#btn-stop-bot").prop("disabled", true);
        }
    }

    updateAppStatusCard();
}

function refreshAppStatus(){
    return $.getJSON("/api/application-status").done(function(res){
        const p = res?.payload||{};
        serviceState.daemon = !!p.zanoDaemonActive;
        serviceState.wallet = !!p.zanoWalletServiceActive;
        serviceState.dex    = !!p.zanoDexTradingServiceActive;
        serviceState.cex    = !!p.zanoCexTradingServiceActive;
        serviceState.app    = !!p.appStarted;
        serviceState.alias    = !!p.walletHasAlias;
        serviceState.pendingAlias    = !!p.walletHasPendingAlias;
        serviceState.tradingOpen = !!p.zanoDexTradingBotActive;

        applyServiceState();
    }).fail(function(){
        // If status endpoint fails, consider all inactive to avoid spamming other endpoints.
        serviceState.daemon = serviceState.wallet = serviceState.dex = serviceState.cex = serviceState.pendingAlias = serviceState.app = false;
        applyServiceState();
    });
}

/***********************
 * Poller manager (no leaks)
 ***********************/
const POLLERS = { zano:null, mm:null, mexc:null, trades:null, app:null };
function startPoller(name, fn, everyMs){
    if(POLLERS[name]) return; // already running
    POLLERS[name] = setInterval(fn, everyMs);
}
function stopPoller(name){
    if(POLLERS[name]){ clearInterval(POLLERS[name]); POLLERS[name]=null; }
}
// Clear on page unload (defensive)
window.addEventListener("beforeunload", ()=>{ Object.keys(POLLERS).forEach(stopPoller); });

/***********************
 * Sync status
 ***********************/
function setSyncState(state){
    const dot=$("#status-dot"), badge=$("#network-state");
    if(Number(state)===2){ dot.removeClass("status-error").addClass("status-ok"); badge.removeClass("bg-secondary bg-danger bg-primary").addClass("bg-success").text("Synced and ready"); }
    else if(Number(state)===1){ dot.removeClass("status-ok").addClass("status-error"); badge.removeClass("bg-secondary bg-success bg-danger").addClass("bg-primary").text("Syncing…"); }
    else { dot.removeClass("status-ok").addClass("status-error"); badge.removeClass("bg-success bg-primary").addClass("bg-secondary").text("Unknown"); }
}

function fetchZanoStatus(){
    if(!serviceState.daemon) return; // gate
    $.getJSON("/api/zano-status")
        .done(function(res){
            const p = (res && res.payload)?res.payload:(res||{});
            const height = Number(p.height)||0;
            const maxSeen = Number(p.max_net_seen_height)||0;
            const state = p.daemon_network_state;

            const percent = computePercentBySpec(height, maxSeen);
            const barWidth = Math.max(0, Math.min(100, percent));
            $("#sync-current").text(fmtNumber(maxSeen));
            $("#sync-max").text(fmtNumber(height));
            $("#sync-percent").text(percent.toFixed(2)+"%");
            $("#sync-bar").css("width", barWidth.toFixed(2)+"%").attr("aria-valuenow", barWidth.toFixed(2)).attr("title", percent.toFixed(2)+"%");

            const behind = Math.max(0, maxSeen - height);
            $("#blocks-behind").text(behind>0 ? `Behind by ${fmtNumber(behind)} blocks` : "Up to date");

            setSyncState(state);
            updateSyncTimestamp();
        })
        .fail(function(jqXHR){
            $("#network-state").removeClass("bg-success bg-primary").addClass("bg-danger").text("Error");
            $("#blocks-behind").text("Failed to fetch /api/zano-status ("+jqXHR.status+")");
            $("#status-dot").removeClass("status-ok").addClass("status-error");
            updateSyncTimestamp();
        });
}

/***********************
 * MEXC status / balances
 ***********************/
function updateMexcBalances(res){
    const $tb=$("#mexc-balances-tbody").empty();
    [["USDT",res?.mexcUsdtBalance],["fUSD",res?.mexcFusdBalance],["ZANO",res?.mexcZanoBalance]]
        .forEach(([asset,val])=>{ $tb.append($("<tr>").append($("<td>").text(asset), $("<td>").text(fmtFloat(val,12)))); });
}
function setMexcStatusUI(statusCode, res, httpCode){
    const badge=$("#mexc-status-badge"), alertBox=$("#mexc-alert"), help=$("#mexc-help"), balancesWrapper=$("#mexc-balances-wrapper");
    function setBadge(cls, text){ badge.removeClass("bg-success bg-danger bg-secondary bg-primary bg-warning").addClass(cls).text(text); }
    if(statusCode===200){
        setBadge("bg-success","OK");
        alertBox.addClass("d-none").text("");
        balancesWrapper.removeClass("d-none");
        updateMexcBalances(res||{});
        help.text("Connected to MEXC API.");
    }else if(statusCode===426){
        setBadge("bg-secondary","No API key");
        alertBox.removeClass("d-none").text("No MEXC API key set. Please set your API key on the settings page.");
        balancesWrapper.addClass("d-none");
        help.text("Status 426: missing API key.");
    }else if(statusCode===424){
        setBadge("bg-danger","Invalid API key");
        alertBox.removeClass("d-none").text("The MEXC API key is incorrect. Please check it on the settings page.");
        balancesWrapper.addClass("d-none");
        help.text("Status 424: invalid API key.");
    }else{
        setBadge("bg-danger","Error");
        alertBox.removeClass("d-none").text("Failed to fetch MEXC status ("+(httpCode||statusCode||"unknown")+")");
        balancesWrapper.addClass("d-none");
        help.text("");
    }
    if (statusCode === 200)      { mexcKeyState = "ok"; }
    else if (statusCode === 426) { mexcKeyState = "missing"; }
    else if (statusCode === 424) { mexcKeyState = "invalid"; }
    else                         { mexcKeyState = null; }
    updateAppStatusCard();
}
function fetchMexcStatus(){
    if(!serviceState.cex) return; // gate
    $.getJSON("/api/bot-data-dash-new")
        .done(function(res,_t,jqXHR){ const code=Number(res?.status)||jqXHR.status||0; setMexcStatusUI(code,res,jqXHR.status); })
        .fail(function(jqXHR){ const code=jqXHR.status; if(code===424||code===426){ setMexcStatusUI(code,null,code); } else { setMexcStatusUI(-1,null,code); } });
}

/***********************
 * Wallet, metrics, orders (from /api/get-mm-status)
 ***********************/
let CURRENT_ALIAS=null, CURRENT_ADDRESS=null, LAST_ASSET_MAP=null, qrcode=null;

function setWalletAlias(aliasStr){
    $("#wallet-alias").text(aliasStr ? String(aliasStr) : "n/a");
    CURRENT_ALIAS = aliasStr || null;
    if(aliasStr && !$("#alias-input").val()) $("#alias-input").val(aliasStr);
}
function setWalletAddress(addressStr){
    $("#wallet-address").text(addressStr ? String(addressStr) : "Error");
    CURRENT_ADDRESS = addressStr || null;
    if(CURRENT_ADDRESS){
        if(!qrcode){
            qrcode = new QRCode("qrcode", { text:"zano:"+CURRENT_ADDRESS, width:128, height:128, colorDark:"#000000", colorLight:"#ffffff", correctLevel: QRCode.CorrectLevel.M });
        }else{
            qrcode.clear();
            qrcode.makeCode("zano:"+CURRENT_ADDRESS);
        }
    }
}
function setAliasNoticeVisible(visible){ $("#alias-alert").toggleClass("d-none", !visible); }
function hasAnyFunds(assetMap){
    if(!assetMap||typeof assetMap!=="object") return false;
    try{ for(const k in assetMap){ const a=assetMap[k]||{}; if(toBigIntVal(a.total??0)>0n || toBigIntVal(a.unlocked??0)>0n) return true; } }catch(_){}
    return false;
}
function updateWalletStatusFromMap(assetBalanceMap){
    const $tb=$("#wallet-tbody").empty();
    if(!assetBalanceMap || Object.keys(assetBalanceMap).length===0){
        $tb.append($("<tr>").append($("<td>").addClass("text-muted").text("—"), $("<td>").addClass("text-muted").text("—"), $("<td>").addClass("text-muted").text("—"), $("<td>").addClass("text-muted").text("—"), $("<td>").addClass("text-muted").text("—")));
        return;
    }
    Object.keys(assetBalanceMap).forEach(assetId=>{
        const a=assetBalanceMap[assetId]||{}, info=a.asset_info||{}, dp=Number(info.decimal_point)||0;
        const $tr=$("<tr>");
        $tr.append(
            $("<td>").text(info.full_name||info.ticker||assetId),
            $("<td>").text(fmtAtomic(a.total??0,dp)),
            $("<td>").text(fmtAtomic(a.unlocked??0,dp)),
            $("<td>").text(fmtAtomic(a.awaiting_in??0,dp)),
            $("<td>").text((a.outs_count??a.utxos??"—").toString())
        );
        $tb.append($tr);
    });
}
function updateBotControls(alias, assetMap){
    const aliasSet=!!alias, fundsOk=hasAnyFunds(assetMap), canStart=aliasSet && fundsOk;
    if (serviceState.tradingOpen) {
        $("#btn-start-bot").prop("disabled", true);
    } else if (canStart) {
        $("#btn-start-bot").prop("disabled", false);
    }

    $("#bot-alert").toggleClass("d-none", canStart);
    let hint="";
    if(!aliasSet && !fundsOk) hint="Alias is not set and no wallet funds detected.";
    else if(!aliasSet) hint="Alias is not set.";
    else if(!fundsOk) hint="No wallet funds detected.";
    $("#bot-help").toggleClass("text-danger", !canStart).toggleClass("text-success", canStart).text(hint);
}
function updateOrdersTable(orders){
    const $tb=$("#orders-tbody").empty();
    const list=Array.isArray(orders)?orders.slice():[];
    list.sort((a,b)=> new Date(b.createdAt) - new Date(a.createdAt));
    if(list.length===0){ $tb.append($("<tr>").append($("<td>").attr("colspan",10).addClass("text-muted").text("No orders"))); $("#orders-count").text("0"); return; }
    list.forEach(o=>{
        const $tr=$("<tr>");
        const typeBadge=$("<span>").addClass("badge " + (o.type==="buy"?"bg-success":(o.type==="sell"?"bg-danger":"bg-secondary"))).text(o.type||"—");
        const sideBadge=$("<span>").addClass("badge bg-primary").text(o.side||"—");
        const statusBadge=$("<span>").addClass("badge " + (o.status==="active"?"bg-primary":"bg-secondary")).text(o.status||"—");
        $tr.append(
            $("<td>").text(o.id??"—"),
            $("<td>").append(typeBadge),
            $("<td>").text(fmtFloat(o.amount,8)),
            $("<td>").text(fmtFloat(o.price,8)),
            $("<td>").text(fmtFloat(o.total,8)),
            $("<td>").text(fmtFloat(o.zanoPrice,6)),
            $("<td>").append(statusBadge),
            $("<td>").text(o.createdAt? new Date(o.createdAt).toLocaleString() : "—"),
        );
        $tb.append($tr);
    });
    $("#orders-count").text(String(list.length));
}
function updateMetricsCard(res){
    const metrics = [
        ["Current ask price", fmtFloat(res.currentAskPrice,12) + " ZANO"],
        ["Current bid price", fmtFloat(res.currentBidPrice,12) + " ZANO"],
        ["Current Zano CEX Price (Average)", res.currentZanoPriceAverage + " USDT"],
        ["Current Zano Price (Ask Weighted)", res.currentZanoPriceAskWeighted + " USDT"],
        ["Current Zano Price (Bid Weighted)", res.currentZanoPriceBidWeighted + " USDT"],
        ["Current Ask Volume (MEXC)", res.currentAskVolume + " ZANO"],
        ["Current Bid Volume (MEXC)", res.currentBidVolume + " ZANO"],
        ["Average Ask Volume (MEXC)", res.averageAskVolume + " ZANO"],
        ["Average Bid Volume (MEXC)", res.averageBidVolume + " ZANO"],
    ];
    const $tb=$("#metrics-tbody").empty();
    metrics.forEach(([k,v])=> $tb.append($("<tr>").append($("<td>").text(k), $("<td>").text(v))) );
    $("#metrics-help").text("Live snapshot from /api/get-mm-status");
}
function fetchMmStatus(){
    if(!serviceState.wallet) return; // gate the endpoint to avoid errors before ready
    $.getJSON("/api/get-mm-status")
        .done(function(res){
            const alias = (res && Object.prototype.hasOwnProperty.call(res, "walletAlias")) ? res.walletAlias : null;
            setWalletAlias(alias||null);
            setAliasNoticeVisible(alias==null);

            const assetBalanceMap = res?.walletsAssetBalanceMap?.main?.assetBalanceMap || {};
            LAST_ASSET_MAP = assetBalanceMap;
            updateWalletStatusFromMap(assetBalanceMap);

            const address = res?.walletsAssetBalanceMap?.main?.walletAdress || null;
            setWalletAddress(address);

            // Only update orders/metrics if DEX/CEX services are active; otherwise skip rendering those parts
            if(serviceState.dex) updateOrdersTable(res.activeOrders||[]);
            if(serviceState.dex) updateMetricsCard(res);

            updateBotControls(CURRENT_ALIAS, LAST_ASSET_MAP);
            updateAppStatusCard();
        })
        .fail(function(jqXHR){
            $("#alias-help").removeClass("text-success").addClass("text-danger").text("Failed to fetch /api/get-mm-status ("+jqXHR.status+")");
            updateBotControls(null, null);
        });
}

/***********************
 * Trades table (flow)
 ***********************/
function badgeForStatus(status, statusString){
    let cls="bg-primary", text="Pending";
    if(typeof status==="number"){ if(status===3){ cls="bg-success"; text="Filled"; } else if(status<0){ cls="bg-danger"; text="Failed"; } }
    if(typeof statusString==="string" && statusString.trim()) text=statusString.trim();
    const $b=$("<span>").addClass("badge "+cls).text(text);
    if(typeof status==="number") $b.attr("title","status="+status);
    return $b;
}
function renderTrades(payload){
    const $tb=$("#trades-tbody").empty();
    const rows=Array.isArray(payload)?payload.slice():[];
    rows.sort((a,b)=> Number(b.timestampLong || +new Date(b.timestamp)) - Number(a.timestampLong || +new Date(a.timestamp)));
    if(rows.length===0){ $tb.append($("<tr>").append($("<td>").attr("colspan",13).addClass("text-muted").text("No trades found"))); $("#rows-count").text("0 rows"); return; }
    rows.forEach(tr=>{
        const tsDex=tr.timestamp||null, fusdSold=tr.tokensTraded, zanoRecv=tr.zanoTraded, zanoUsdtRef=tr.zanoUsdtPrice;
        const mx=tr.zanoSellOrder||null, mxZano=mx?mx.firstAmount:null, mxPrice=mx?mx.price:null, mxUsdtT=mx?mx.secondAmountTarget:null, mxStatus=mx?mx.status:null, mxStr=mx?mx.statusString:null, mxTs=mx?mx.timestamp:null;
        const fb=tr.fusdBuyOrder||null, fbFusd=fb?fb.firstAmount:null, fbPrice=fb?fb.price:null, fbUsdt=fb?fb.secondAmount:null, fbStatus=fb?fb.status:null, fbStr=fb?fb.statusString:null, fbTs=fb?fb.timestamp:null;

        const $tr=$("<tr>");
        $tr.append(
            $("<td>").text(new Date(tsDex).toLocaleString()),
            $("<td>").text(fmtFloat(fusdSold, tr.decimals??8)),
            $("<td>").text(fmtFloat(zanoRecv, 12)),
            $("<td class='colsecr'>").text(fmtFloat(zanoUsdtRef,8)),
            $("<td>").text(fmtFloat(mxZano,12)),
            $("<td>").text(fmtFloat(mxPrice,6)),
            $("<td>").text(fmtFloat(mxUsdtT,6)),
            $("<td class='colsecr'>").append(mx?badgeForStatus(mxStatus,mxStr):$("<span>").addClass("text-muted").text("—"))
                .append(mx? $("<div>").addClass("text-muted small").text(new Date(mxTs).toLocaleString()):""),
            $("<td>").text(fb?fmtFloat(fbFusd,8):"—"),
            $("<td>").text(fb?fmtFloat(fbPrice,8):"—"),
            $("<td>").text(fb?fmtFloat(fbUsdt,8):"—"),
            $("<td>").append(fb?badgeForStatus(fbStatus,fbStr):$("<span>").addClass("text-muted").text("—"))
                .append(fb? $("<div>").addClass("text-muted small").text(new Date(fbTs).toLocaleString()):"")
        );
        if(typeof mxStatus==="number" && mxStatus<0){ $tr.attr("title","MEXC sell failed"); }
        $tb.append($tr);
    });
    $("#rows-count").text(String(rows.length) + (rows.length===1 ? " row" : " rows"));
}
function fetchTrades(){
    if(!serviceState.dex) { // gate (avoid spamming before ready)
        $("#trades-tbody").html('<tr><td class="text-muted" colspan="13">DEX service not ready…</td></tr>');
        $("#rows-count").text("0 rows");
        return;
    }
    $.getJSON("/api/extended-trade-data")
        .done(function(res){ renderTrades(res?.payload||[]); updateTradesTimestamp(); })
        .fail(function(jqXHR){
            const $tb=$("#trades-tbody").empty();
            $tb.append($("<tr>").append($("<td>").attr("colspan",13).addClass("text-danger").text("Failed to fetch /api/extended-trade-data ("+jqXHR.status+")")));
            $("#rows-count").text("0 rows"); updateTradesTimestamp();
        });
}

function renderRowsBuy(payload) {
    const $tb = $("#trades-tbody-buy").empty();
    const rows = Array.isArray(payload) ? payload.slice() : [];

    // Newest first
    rows.sort((a, b) => (Number(b.timestampLong || +new Date(b.timestamp)) - Number(a.timestampLong || +new Date(a.timestamp))));

    if (rows.length === 0) {
        $tb.append($("<tr>").append($("<td>").attr("colspan", 13).addClass("text-muted").text("No trades found")));
        $("#rows-count-buy").text("0 rows");
        console.log("No buy trades");
        return;
    }

    rows.forEach(tr => {
        // DEX step
        const tsDex = tr.timestamp || null;
        const fusdSold = tr.tokensTraded;
        const zanoReceived = tr.zanoTraded;
        const zanoUsdtRef = tr.zanoUsdtPrice;

        // MEXC SELL step
        const mx = tr.zanoBuyOrder || null;
        const mxZano = mx ? mx.firstAmount : null;
        const mxPrice = mx ? mx.price : null;
        const mxUsdt = mx ? mx.secondAmount : null;
        const mxUsdtTarget = mx ? mx.secondAmountTarget : null;
        const mxSoldPercent = (mxUsdtTarget / mxPrice) / mxZano * 100;
        var mxStatus = mx ? mx.status : null;
        if (mxStatus == 1) {
            mxStatus = 3;
        }
        const mxStatusStr = mx ? mx.statusString : null;
        const mxTs = mx ? mx.timestamp : null;
        var zanoSoldString = " ";

        const $tr = $("<tr>");

        // Cols
        $tr.append(
            $("<td>").text(fmtTime(tsDex)),

            // DEX: fUSD sold, ZANO received, ref price
            $("<td>").text(fmtFloat(fusdSold, tr.decimals ?? 8)),
            $("<td>").text(fmtFloat(zanoReceived, 12)),
            $("<td class='colsecr'>").text(fmtFloat(zanoUsdtRef, 8)),

            // MEXC SELL
            $("<td>").text(fmtFloat(mxZano, 12) + zanoSoldString),
            $("<td>").text(fmtFloat(mxPrice, 6)),
            $("<td>").text(fmtFloat(mxUsdt, 6)),
            $("<td>").append(mx ? badgeForStatus(mxStatus, mxStatusStr) : $("<span>").addClass("text-muted").text("—"))
                .append(mx ? $("<div>").addClass("text-muted small").text(fmtTime(mxTs)) : ""),
        );

        // Optional visual hint: if MEXC SELL failed, emphasize the row slightly by title
        if (typeof mxStatus === "number" && mxStatus < 0) {
            $tr.attr("title", "MEXC sell failed");
        }

        $tb.append($tr);
    });

    $("#rows-count-buy").text(String(rows.length) + (rows.length === 1 ? " row" : " rows"));
}

/* ---------------- Data Fetch ---------------- */
function fetchTradesBuy() {
    $.getJSON("/api/extended-trade-data-buy")
        .done(function (res) {
            const payload = (res && res.payload) ? res.payload : [];
            renderRowsBuy(payload);
            updateTimestamp();
        })
        .fail(function (jqXHR) {
            const $tb = $("#trades-tbody-buy").empty();
            $tb.append(
                $("<tr>").append(
                    $("<td>").attr("colspan", 13)
                        .addClass("text-danger")
                        .text("Failed to fetch /api/extended-trade-data (" + jqXHR.status + ")")
                )
            );
            $("#rows-count-buy").text("0 rows");
            updateTimestamp();
        });
}

/***********************
 * Init + handlers
 ***********************/
$(function(){
    daemonModal = new bootstrap.Modal(document.getElementById("daemon-modal"), { backdrop:"static", keyboard:false });

    // Initial readiness check; then start readiness poller
    refreshAppStatus().then(()=>{
        // Kick initial fetches (they internally gate by serviceState):
        fetchZanoStatus(); fetchMmStatus(); fetchMexcStatus(); fetchTrades(); fetchTradesBuy();
    });
    startPoller("app", refreshAppStatus, 4000);

    // Save alias
    $("#alias-save-btn").on("click", function(){
        const $btn=$(this), $input=$("#alias-input"), $help=$("#alias-help");
        const aliasVal = ($input.val()||"").toString().trim();
        if(!aliasVal){ $help.removeClass("text-success").addClass("text-danger").text("Alias cannot be empty."); return; }
        if(aliasVal.length<3 || aliasVal.length>64){ $help.removeClass("text-success").addClass("text-danger").text("Alias must be 3–64 characters."); return; }
        $btn.prop("disabled", true); $help.removeClass("text-danger text-success").text("Saving…");
        $.ajax({ url:"/api/set-alias", method:"POST", contentType:"application/json", data: JSON.stringify({ alias: aliasVal }) })
            .done(function(){ $help.removeClass("text-danger").addClass("text-success").text("Alias saved."); setWalletAlias(aliasVal); setAliasNoticeVisible(false); updateBotControls(CURRENT_ALIAS, LAST_ASSET_MAP); })
            .fail(function(jqXHR){
                console.log(jqXHR);
                if (jqXHR.responseJSON && jqXHR.responseJSON.message && jqXHR.responseJSON.message.includes("WALLET_RPC_ERROR_CODE_NOT_ENOUGH_MONEY")) {
                    $help.removeClass("text-success").addClass("text-danger").text("Failed to save alias, not enough money in wallet.");
                } else {
                    $help.removeClass("text-success").addClass("text-danger").text("Failed to save alias ("+jqXHR.status+").");
                }
            })
            .always(function(){ $btn.prop("disabled", false); });
    });

    // Start/Stop bot
    $("#btn-start-bot").on("click", function(){
        const $btn=$(this), $help=$("#bot-help");
        $btn.prop("disabled", true); $("#btn-stop-bot").prop("disabled", true);
        $help.removeClass("text-danger").addClass("text-muted").text("Starting…");
        $.ajax({ url:"/api/bot/start", method:"POST", contentType:"application/json", data: JSON.stringify({ cmd: 'start' }) })
            .done(function(){ $help.removeClass("text-danger").addClass("text-success").text("Bot started."); serviceState.tradingOpen = true; })
            .fail(function(jqXHR){ $help.removeClass("text-success").addClass("text-danger").text("Failed to start bot ("+jqXHR.status+")."); })
            .always(function(){ updateBotControls(CURRENT_ALIAS, LAST_ASSET_MAP); $("#btn-stop-bot").prop("disabled", false); fetchMmStatus(); });
    });
    $("#btn-stop-bot").on("click", function(){
        const $btn=$(this), $help=$("#bot-help");
        $btn.prop("disabled", true); $("#btn-start-bot").prop("disabled", true);
        $help.removeClass("text-danger").addClass("text-muted").text("Stopping…");
        $.ajax({ url:"/api/bot/stop", method:"POST", contentType:"application/json", data: JSON.stringify({ cmd: 'stop' })  })
            .done(function(){ $help.removeClass("text-danger").addClass("text-success").text("Bot stopped."); })
            .fail(function(jqXHR){ $help.removeClass("text-success").addClass("text-danger").text("Failed to stop bot ("+jqXHR.status+")."); })
            .always(function(){ updateBotControls(CURRENT_ALIAS, LAST_ASSET_MAP); fetchMmStatus(); });
    });

    // Trades refresh button
    $("#refresh-btn").on("click", fetchTrades);
});

function iconYesNo(flag){
    return flag
        ? '<i class="bi bi-check-circle-fill text-success me-1"></i>Yes'
        : '<i class="bi bi-x-circle-fill text-danger me-1"></i>No';
}

function atomicToNum(atomic, decimals){
    const n = Number(atomic);
    const d = Math.max(0, Number(decimals) || 0);
    if (!isFinite(n)) return 0;
    return n / Math.pow(10, d);
}

function walletHasMinZano(minRequired = 0.11){
    try {
        const map = LAST_ASSET_MAP || {};
        for(const k in map){
            const a = map[k] || {};
            const info = a.asset_info || {};
            const ticker = (info.ticker || "").toUpperCase();
            if (ticker === "ZANO"){
                const unlocked = atomicToNum(a.unlocked ?? 0, info.decimal_point ?? 0);
                if (unlocked >= minRequired) return true;
            }
        }
    } catch(_) {}
    return false;
}

function updateAppStatusCard(){
    // Icons (Yes/No)
    $("#appstat-wallet").html(iconYesNo(!!serviceState.wallet));
    $("#appstat-dex").html(iconYesNo(!!serviceState.dex));
    $("#appstat-cex").html(iconYesNo(!!serviceState.cex));

    // Guidance text
    const hints = [];
    // Wallet: needs ≥ 0.11 ZANO to be able to set an alias
    if (!serviceState.wallet) {
        hints.push("Wallet service is not active yet. If freshly started, give it a moment. To set an alias, deposit at least 0.11 ZANO.");
    } else {
        if (!serviceState.alias) {
            if (!walletHasMinZano(0.11)) {
                hints.push("Deposit at least 0.11 ZANO so you can create a wallet alias.");
            }
        }
    }

    // DEX: depends on wallet alias
    if (!serviceState.dex) {
        if (!CURRENT_ALIAS) {
            hints.push("Set a wallet alias (see the “Wallet Status” card) to enable the DEX trading service.");
        } else {
            hints.push("DEX trading service is inactive. It requires a valid wallet alias.");
        }
    }

    // CEX: depends on MEXC API key
    if (!serviceState.cex) {
        if (mexcKeyState === "missing") {
            hints.push('No MEXC API key set. Add your API key and secret on the <a href="/settings">Settings</a> page.');
        } else if (mexcKeyState === "invalid") {
            hints.push('MEXC API key appears invalid. Fix it on the <a href="/settings">Settings</a> page.');
        } else {
            hints.push('Ensure your MEXC API key and secret are configured on the <a href="/settings">Settings</a> page.');
        }
    }

    // Final message
    $("#appstatus-help").html(
        hints.length ? ("<ul class='mb-0 ps-3'>" + hints.map(h => `<li>${h}</li>`).join("") + "</ul>") :
            "<span class='text-success'>All application services are active.</span>"
    );



}