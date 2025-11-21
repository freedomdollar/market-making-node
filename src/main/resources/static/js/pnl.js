/***********************
 * PnL (top bar)
 ***********************/
function setPnlUI(payload){
    const total = Number(payload?.totalUsdt);
    const sell  = Number(payload?.sellFlowUsdt);
    const buy   = Number(payload?.buyFlowUsdt);
    const nSell = Number(payload?.sellTradesCount) || 0;
    const nBuy  = Number(payload?.buyTradesCount) || 0;

    const $badge = $("#pnl-badge");
    const $text  = $("#pnl-breakdown");

    // Color the pill by sign
    const cls = total > 0 ? "bg-success" : (total < 0 ? "bg-danger" : "bg-secondary");
    $badge.removeClass("bg-success bg-danger bg-secondary").addClass(cls);

    // Texts
    $badge.text(`PnL: ${fmtFloat(total, 6)} USDT`);
    $badge.attr("data-bs-title", `Realized PnL — Total: ${fmtFloat(total,6)} USDT`);
    $text.text(`Sell ${fmtFloat(sell,6)} • Buy ${fmtFloat(buy,6)} • ${nSell + nBuy} trades`);

    // Refresh tooltip (Bootstrap 5)
    const tip = bootstrap.Tooltip.getInstance($badge[0]);
    if (tip) tip.setContent({ '.tooltip-inner': $badge.attr("data-bs-title") });
    else new bootstrap.Tooltip($badge[0]);
}

function setPnlError(message){
    const $badge = $("#pnl-badge");
    const $text  = $("#pnl-breakdown");
    $badge.removeClass("bg-success bg-danger").addClass("bg-secondary").text("PnL: —");
    $badge.attr("data-bs-title", message || "PnL unavailable");
    $text.text("—");
    const tip = bootstrap.Tooltip.getInstance($badge[0]);
    if (tip) tip.setContent({ '.tooltip-inner': $badge.attr("data-bs-title") });
    else new bootstrap.Tooltip($badge[0]);
}

function fetchPnlSummary(){
    $.getJSON("/api/pnl-data")
        .done(function(res, _t, jqXHR){
            const code = Number(res?.status) || jqXHR.status || 0;
            if (code === 200) {
                setPnlUI(res.payload || {});
            } else {
                setPnlError(`PnL endpoint returned status ${code}`);
            }
        })
        .fail(function(jqXHR){
            setPnlError(`Failed to fetch /api/pnl-data (${jqXHR.status})`);
        });
}

function startPollerPnl(name, fn, everyMs){
    if(POLLERS[name]) return; // already running
    POLLERS[name] = setInterval(fn, everyMs);
}
function stopPollerPnl(name){
    if(POLLERS[name]){ clearInterval(POLLERS[name]); POLLERS[name]=null; }
}

fetchPnlSummary();
startPollerPnl("pnl", fetchPnlSummary, 10000);
