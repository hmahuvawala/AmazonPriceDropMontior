const state = {
    products: [],
    selectedId: null,
    chart: null,
};

async function fetchJson(url, options = {}) {
    const response = await fetch(url, {
        headers: {"Content-Type": "application/json", ...options.headers},
        ...options,
    });
    if (!response.ok) {
        const detail = await response.text();
        throw new Error(detail || response.statusText);
    }
    if (response.status === 204) {
        return null;
    }
    const text = await response.text();
    return text ? JSON.parse(text) : null;
}

function formatLastPrice(product) {
    if (product.lastPrice == null) {
        return {text: "—", empty: true};
    }
    const cur = product.lastPriceCurrency || "USD";
    const n = Number(product.lastPrice);
    try {
        return {
            text: n.toLocaleString(undefined, {
                style: "currency",
                currency: cur,
                minimumFractionDigits: 2,
                maximumFractionDigits: 2,
            }),
            empty: false,
        };
    } catch {
        return {text: `$${n.toFixed(2)}`, empty: false};
    }
}

function formatThresholdSummary(product) {
    const parts = [];
    if (product.thresholdPct != null) {
        parts.push(`${product.thresholdPct}%`);
    }
    if (product.thresholdAmount != null) {
        parts.push(`$${Number(product.thresholdAmount).toFixed(2)}`);
    }
    return parts.length ? parts.join(" · ") : "—";
}

function setMessage(text, isError = false) {
    const el = document.getElementById("form-message");
    el.textContent = text || "";
    el.classList.toggle("form-message--error", Boolean(isError && text));
}

function setSchedulerSettingsMessage(text, isError = false) {
    const el = document.getElementById("scheduler-settings-message");
    el.textContent = text || "";
    el.classList.toggle("form-message--error", Boolean(isError && text));
}

function setRecipientsMessage(text, isError = false) {
    const el = document.getElementById("recipients-message");
    el.textContent = text || "";
    el.classList.toggle("form-message--error", Boolean(isError && text));
}

async function loadSchedulerSettings() {
    const s = await fetchJson("/api/admin/scheduler-settings");
    const input = document.getElementById("scheduler-interval-minutes");
    input.value = String(Math.round(s.checkIntervalMs / 60000));
}

async function loadRecipients() {
    const r = await fetchJson("/api/admin/notification-recipients");
    document.getElementById("email-recipients").value = r.emailToCsv || "";
    document.getElementById("sms-recipients").value = r.smsToCsv || "";
}

function renderProductList() {
    const list = document.getElementById("product-list");
    const empty = document.getElementById("product-empty");
    list.innerHTML = "";
    if (!state.products.length) {
        empty.hidden = false;
        return;
    }
    empty.hidden = true;
    state.products.forEach((product) => {
        const li = document.createElement("li");
        li.className = "product-item" + (product.id === state.selectedId ? " is-selected" : "");
        li.setAttribute("role", "button");
        li.setAttribute("tabindex", "0");
        const label = product.displayName || "listing";
        li.setAttribute("aria-label", "View price chart for " + label);
        li.addEventListener("click", () => {
            selectProduct(product.id).catch((err) => setMessage(err.message, true));
        });
        li.addEventListener("keydown", (e) => {
            if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                selectProduct(product.id).catch((err) => setMessage(err.message, true));
            }
        });

        const body = document.createElement("div");
        body.className = "product-item__body";

        const main = document.createElement("div");
        main.className = "product-item__main";

        const title = document.createElement("div");
        title.className = "product-title";
        title.textContent = product.displayName || product.amazonUrl;

        const meta = document.createElement("div");
        meta.className = "product-meta";
        meta.textContent = formatThresholdSummary(product);

        main.append(title, meta);

        const aside = document.createElement("div");
        aside.className = "product-item__aside";

        const priceInfo = formatLastPrice(product);
        const priceEl = document.createElement("div");
        priceEl.className = priceInfo.empty ? "product-price product-price--empty" : "product-price";
        priceEl.textContent = priceInfo.text;

        const actions = document.createElement("div");
        actions.className = "product-actions";

        const deleteBtn = document.createElement("button");
        deleteBtn.type = "button";
        deleteBtn.className = "btn btn--danger";
        deleteBtn.textContent = "Remove";
        deleteBtn.addEventListener("click", (event) => {
            event.stopPropagation();
            deleteProduct(product.id);
        });

        actions.append(deleteBtn);
        aside.append(priceEl, actions);
        body.append(main, aside);

        li.append(body);
        list.appendChild(li);
    });
}

async function loadProducts() {
    state.products = await fetchJson("/api/products");
    renderProductList();
}

function cssVar(name, fallback) {
    const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return value || fallback;
}

async function selectProduct(id) {
    state.selectedId = id;
    renderProductList();
    const hint = document.getElementById("chart-hint");
    hint.hidden = true;
    const history = await fetchJson(`/api/products/${id}/price-history`);
    const successes = history.filter((row) => row.success && row.price != null);
    const labels = successes.map((row) => new Date(row.checkedAt).toLocaleString(undefined, {dateStyle: "short", timeStyle: "short"}));
    const data = successes.map((row) => Number(row.price));
    const pointJsoup = cssVar("--chart-point-jsoup", "#4d6b42");
    const pointAlter = cssVar("--chart-point-alter", "#9a452e");
    const colors = successes.map((row) => (row.method === "ALTERLAB" ? pointAlter : pointJsoup));
    const lineColor = cssVar("--chart-line", "#b3523a");
    const fillColor = cssVar("--chart-fill", "rgba(179, 82, 58, 0.1)");
    const gridColor = cssVar("--chart-grid", "rgba(20, 20, 19, 0.06)");
    const tickColor = cssVar("--chart-tick", "#6b6a65");
    const ctx = document.getElementById("price-chart").getContext("2d");
    if (state.chart) {
        state.chart.destroy();
    }
    state.chart = new Chart(ctx, {
        type: "line",
        data: {
            labels,
            datasets: [
                {
                    label: "Price",
                    data,
                    tension: 0.2,
                    borderColor: lineColor,
                    backgroundColor: fillColor,
                    pointBackgroundColor: colors,
                    pointBorderColor: lineColor,
                    pointBorderWidth: 1,
                    pointRadius: 3,
                    borderWidth: 2,
                    fill: true,
                },
            ],
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {intersect: false, mode: "index"},
            scales: {
                y: {
                    beginAtZero: false,
                    title: {display: false},
                    ticks: {color: tickColor, font: {size: 11}},
                    grid: {color: gridColor},
                },
                x: {
                    ticks: {maxRotation: 0, color: tickColor, font: {size: 10}, maxTicksLimit: 8},
                    grid: {display: false},
                },
            },
            plugins: {
                legend: {display: false},
            },
        },
    });
}

async function runChecksAndRefresh() {
    await fetchJson("/api/admin/run-checks", {method: "POST"});
    await loadProducts();
    setMessage("Checks complete.");
    if (state.selectedId) {
        await selectProduct(state.selectedId);
    }
}

async function deleteProduct(id) {
    if (!window.confirm("Remove this listing and its history?")) {
        return;
    }
    await fetchJson(`/api/products/${id}`, {method: "DELETE"});
    if (state.selectedId === id) {
        state.selectedId = null;
        if (state.chart) {
            state.chart.destroy();
            state.chart = null;
        }
        document.getElementById("chart-hint").hidden = false;
    }
    await loadProducts();
}

document.getElementById("add-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const formData = new FormData(event.target);
    const pctRaw = formData.get("thresholdPct");
    const amtRaw = formData.get("thresholdAmount");
    const pctNum = pctRaw === "" || pctRaw == null ? null : Number(pctRaw);
    const amtNum = amtRaw === "" || amtRaw == null ? null : Number(amtRaw);
    const payload = {
        amazonUrl: formData.get("amazonUrl"),
        displayName: String(formData.get("displayName") || "").trim(),
        active: true,
    };
    if (pctNum != null && !Number.isNaN(pctNum)) {
        payload.thresholdPct = pctNum;
    }
    if (amtNum != null && !Number.isNaN(amtNum)) {
        payload.thresholdAmount = amtNum;
    }
    try {
        await fetchJson("/api/products", {method: "POST", body: JSON.stringify(payload)});
        event.target.reset();
        setMessage("Added.");
        await loadProducts();
        window.setTimeout(() => {
            runChecksAndRefresh().catch((err) => setMessage(err.message, true));
        }, 3000);
    } catch (error) {
        setMessage(error.message, true);
    }
});

document.getElementById("run-checks").addEventListener("click", async () => {
    try {
        await runChecksAndRefresh();
    } catch (error) {
        setMessage(error.message, true);
    }
});

document.getElementById("save-recipients").addEventListener("click", async () => {
    const emailToCsv = document.getElementById("email-recipients").value.trim();
    const smsToCsv = document.getElementById("sms-recipients").value.trim();
    try {
        const updated = await fetchJson("/api/admin/notification-recipients", {
            method: "PUT",
            body: JSON.stringify({emailToCsv, smsToCsv}),
        });
        document.getElementById("email-recipients").value = updated.emailToCsv || "";
        document.getElementById("sms-recipients").value = updated.smsToCsv || "";
        setRecipientsMessage("Saved.");
    } catch (error) {
        setRecipientsMessage(error.message, true);
    }
});

document.getElementById("save-scheduler-interval").addEventListener("click", async () => {
    const input = document.getElementById("scheduler-interval-minutes");
    const minutes = Number(input.value);
    if (!Number.isFinite(minutes) || minutes < 1 || minutes > 10080) {
        setSchedulerSettingsMessage("Enter minutes between 1 and 10080 (7 days).", true);
        return;
    }
    const ms = Math.round(minutes * 60000);
    try {
        const updated = await fetchJson("/api/admin/scheduler-settings", {
            method: "PUT",
            body: JSON.stringify({checkIntervalMs: ms}),
        });
        input.value = String(Math.round(updated.checkIntervalMs / 60000));
        setSchedulerSettingsMessage("Saved.");
    } catch (error) {
        setSchedulerSettingsMessage(error.message, true);
    }
});

Promise.all([loadProducts(), loadSchedulerSettings(), loadRecipients()]).catch((error) => {
    setMessage(error.message, true);
    setSchedulerSettingsMessage(error.message, true);
    setRecipientsMessage(error.message, true);
});
