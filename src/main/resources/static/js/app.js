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

function setMessage(text, isError = false) {
    const el = document.getElementById("form-message");
    el.textContent = text || "";
    el.classList.toggle("form-message--error", Boolean(isError && text));
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
        const title = document.createElement("div");
        title.className = "product-title";
        title.textContent = product.displayName || product.amazonUrl;
        const meta = document.createElement("div");
        meta.className = "product-meta";
        meta.textContent = `Threshold ${product.thresholdPct}% · ${product.active ? "active" : "paused"}`;
        const actions = document.createElement("div");
        actions.className = "product-actions";
        const selectBtn = document.createElement("button");
        selectBtn.type = "button";
        selectBtn.textContent = "History";
        selectBtn.addEventListener("click", () => selectProduct(product.id));
        const deleteBtn = document.createElement("button");
        deleteBtn.type = "button";
        deleteBtn.textContent = "Remove";
        deleteBtn.className = "danger";
        deleteBtn.addEventListener("click", () => deleteProduct(product.id));
        actions.append(selectBtn, deleteBtn);
        li.append(title, meta, actions);
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
    document.getElementById("chart-hint").hidden = true;
    const history = await fetchJson(`/api/products/${id}/price-history`);
    const successes = history.filter((row) => row.success && row.price != null);
    const labels = successes.map((row) => new Date(row.checkedAt).toLocaleString());
    const data = successes.map((row) => Number(row.price));
    const pointJsoup = cssVar("--chart-point-jsoup", "#5a6e4e");
    const pointAlter = cssVar("--chart-point-alter", "#a84a30");
    const colors = successes.map((row) => (row.method === "ALTERLAB" ? pointAlter : pointJsoup));
    const lineColor = cssVar("--chart-line", "#b85538");
    const fillColor = cssVar("--chart-fill", "rgba(184, 85, 56, 0.12)");
    const gridColor = cssVar("--chart-grid", "rgba(28, 27, 25, 0.08)");
    const tickColor = cssVar("--chart-tick", "#6f6e69");
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
                    label: "Price (USD)",
                    data,
                    tension: 0.25,
                    borderColor: lineColor,
                    backgroundColor: fillColor,
                    pointBackgroundColor: colors,
                    pointBorderColor: lineColor,
                    pointBorderWidth: 1,
                    borderWidth: 2,
                    fill: true,
                },
            ],
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: false,
                    title: {display: true, text: "Price", color: tickColor},
                    ticks: {color: tickColor},
                    grid: {color: gridColor},
                },
                x: {
                    ticks: {maxRotation: 45, minRotation: 30, color: tickColor},
                    grid: {color: gridColor},
                },
            },
            plugins: {
                legend: {
                    display: true,
                    labels: {
                        color: tickColor,
                        font: {family: "Inter", size: 12},
                    },
                },
            },
        },
    });
}

async function deleteProduct(id) {
    if (!window.confirm("Remove this product and its history?")) {
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
    const payload = {
        amazonUrl: formData.get("amazonUrl"),
        displayName: formData.get("displayName") || null,
        thresholdPct: Number(formData.get("thresholdPct")),
        active: true,
    };
    try {
        await fetchJson("/api/products", {method: "POST", body: JSON.stringify(payload)});
        event.target.reset();
        const threshold = document.querySelector('#add-form [name="thresholdPct"]');
        if (threshold) {
            threshold.value = "5";
        }
        setMessage("Product saved.");
        await loadProducts();
    } catch (error) {
        setMessage(error.message, true);
    }
});

document.getElementById("run-checks").addEventListener("click", async () => {
    try {
        await fetchJson("/api/admin/run-checks", {method: "POST"});
        setMessage("Checks triggered. Refresh history after a few seconds.");
        if (state.selectedId) {
            await selectProduct(state.selectedId);
        }
    } catch (error) {
        setMessage(error.message, true);
    }
});

loadProducts().catch((error) => setMessage(error.message, true));
