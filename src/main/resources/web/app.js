let currentUserId = null;
const API_URL = "http://localhost:9091/api"; 

// --- INITIALIZATION ---
(function init() {
    try {
        const uid = localStorage.getItem('userId');
        currentUserId = uid ? parseInt(uid) : null;
        
        const isLoginPage = window.location.pathname.includes('login.html');

        // Redirect to login if not auth and not on login page
        if(!currentUserId && !isLoginPage) {
            window.location.href = 'login.html';
            return;
        }

        // Redirect to index if auth and on login page
        if(currentUserId && isLoginPage) {
            window.location.href = 'index.html';
            return;
        }
    } catch(e) { currentUserId = null; }

    if(currentUserId) {
        // Welcome message
        const name = localStorage.getItem('userName');
        const w = document.getElementById('welcome-msg');
        if(w && name) w.textContent = `Bonjour, ${name}`;

        // Initial Data Load
        loadEmprunts();
        searchLivres(); // Load all books initially
        loadPenalites();

        // Auto-refresh (Reactive interface)
        setInterval(checkNotifications, 10000); // Notifications
        setInterval(loadEmprunts, 5000);        // Status of returns
    }
})();

// --- AUTHENTICATION ---
function login() {
    const cin = document.getElementById('cin').value;
    const pass = document.getElementById('password').value;
    const btn = document.getElementById('btn-login');
    
    if(btn) btn.textContent = 'Connexion...';

    fetch(`${API_URL}/auth?cin=${cin}&pass=${pass}`, { method: 'POST' })
        .then(res => res.json())
        .then(data => {
            if(data.success) {
                currentUserId = data.id;
                localStorage.setItem('userId', data.id);
                
                // Fetch user details to get the name
                fetch(`${API_URL}?etudiantId=${data.id}`)
                    .then(r => r.json())
                    .then(user => {
                        if(user && user.nom) localStorage.setItem('userName', user.nom);
                        else localStorage.setItem('userName', cin);
                        window.location.href = 'index.html';
                    })
                    .catch(() => {
                        localStorage.setItem('userName', cin);
                        window.location.href = 'index.html';
                    });
            } else {
                showToast('Identifiants incorrects', 'error');
                if(btn) btn.textContent = 'Se connecter';
            }
        })
        .catch(err => {
            showToast('Erreur de connexion serveur', 'error');
            if(btn) btn.textContent = 'Se connecter';
        });
}

function logout() {
    localStorage.removeItem('userId');
    localStorage.removeItem('userName');
    window.location.href = 'login.html';
}

// --- BOOKS CATALOG ---
function handleSearch(event) {
    if (event.key === 'Enter') {
        searchLivres();
    }
}

function searchLivres() {
    const input = document.getElementById('search-input');
    const q = input ? input.value : '';
    const grid = document.getElementById('books-grid');
    
    // Don't clear immediately to avoid flickering if we want smooth updates, 
    // but for search we should show loading state if query changed.
    // simple approach:
    // grid.innerHTML = '<div class="loading-skeleton" ...></div>'; 

    fetch(`${API_URL}/livres?q=${q}`)
        .then(res => res.json())
        .then(livres => {
            if(!grid) return;
            grid.innerHTML = '';
            
            if(livres.length === 0) {
                grid.innerHTML = '<p style="grid-column: 1/-1; text-align: center; color: var(--text-muted);">Aucun livre trouvé.</p>';
                return;
            }

            livres.forEach(l => {
                const card = document.createElement('div');
                card.className = 'book-card';
                
                // Status badge
                const statusClass = l.disponible ? 'status-yes' : 'status-no';
                const statusText = l.disponible ? 'Disponible' : 'Indisponible';
                const statusIcon = l.disponible ? '🟢' : '🔴';

                // Action Button
                let actionBtn = '';
                if(l.disponible) {
                    actionBtn = `<button class="btn btn-primary btn-action" onclick="emprunter(${l.id})">Emprunter</button>`;
                } else {
                    actionBtn = `<button class="btn btn-outline btn-action" onclick="reserver(${l.id})">Réserver</button>`;
                }

                card.innerHTML = `
                    <span class="book-cat">Livre</span>
                    <div class="book-title">${l.titre}</div>
                    <div class="book-author">par ${l.auteur}</div>
                    <div class="book-status ${statusClass}">
                        ${statusIcon} ${statusText}
                    </div>
                    ${actionBtn}
                `;
                grid.appendChild(card);
            });
        })
        .catch(err => console.error('Error loading books', err));
}

// --- EMPRUNTS (Sidebar) ---
function loadEmprunts() {
    if(!currentUserId) return;
    fetch(`${API_URL}/emprunts?etudiantId=${currentUserId}`)
        .then(res => res.json())
        .then(list => {
            const container = document.getElementById('emprunts-list');
            const countBadge = document.getElementById('emprunt-count');
            if(!container) return;

            // Update count
            if(countBadge) countBadge.textContent = list ? list.length : 0;

            if(!list || list.length === 0) { 
                container.innerHTML = '<p style="color: var(--text-muted); font-size: 0.9rem;">Aucun emprunt.</p>'; 
                return; 
            }

            // Rebuild list (simplest way to ensure sync)
            container.innerHTML = '';
            list.forEach(e => {
                const item = document.createElement('div');
                item.className = 'emprunt-item';
                
                let statusHtml = '';
                if (e.pendingReturn) {
                    statusHtml = `<div style="color: var(--warning); font-size: 0.8rem; font-weight: 600;">⏳ Retour en attente validation</div>`;
                } else {
                    statusHtml = `<button onclick="retour(${e.livreId})" class="btn btn-primary" style="padding: 0.3rem; font-size: 0.8rem;">Retourner</button>`;
                }

                item.innerHTML = `
                    <div class="emprunt-title">Livre #${e.livreId}</div>
                    <div class="emprunt-date">Emprunté le: ${e.dateEmprunt}</div>
                    ${statusHtml}
                `;
                container.appendChild(item);
            });
        })
        .catch(e => console.error(e));
}

// --- ACTIONS ---
function emprunter(livreId) {
    showToast('Traitement de l\'emprunt...', 'info');
    fetch(`${API_URL}/emprunts?etudiantId=${currentUserId}&livreId=${livreId}`, { method: 'POST' })
        .then(res => res.json())
        .then(res => {
            if(res.success) { 
                showToast('Livre emprunté avec succès !', 'success'); 
                loadEmprunts(); 
                searchLivres(); // Update availability
            }
            else { 
                showToast(res.message || 'Impossible d\'emprunter', 'error'); 
            }
        })
        .catch(() => showToast('Erreur réseau', 'error'));
}

function retour(livreId) {
    // Optimistic UI update could happen here, but we wait for server confirmation
    showToast('Demande de retour envoyée...', 'info');
    fetch(`${API_URL}/return?etudiantId=${currentUserId}&livreId=${livreId}`, { method: 'POST' })
        .then(res => res.json())
        .then(obj => {
            if (obj && obj.success) {
                showToast('Retour enregistré, en attente de validation admin.', 'success');
            } else {
                showToast(obj.message || 'Erreur lors du retour', 'error');
            }
            loadEmprunts(); // Will show "pending" status
        })
        .catch(() => showToast('Erreur réseau', 'error'));
}

function reserver(livreId) {
    fetch(`${API_URL}/reservations?etudiantId=${currentUserId}&livreId=${livreId}`, { method: 'POST' })
        .then(res => res.json())
        .then(() => showToast('Réservation confirmée. Vous serez notifié.', 'success'))
        .catch(() => showToast('Erreur réservation', 'error'));
}

// --- NOTIFICATIONS & PENALITIES ---
function checkNotifications() {
    if (!currentUserId) return;
    fetch(`${API_URL}/notifications?etudiantId=${currentUserId}`)
        .then(res => res.json())
        .then(notifications => {
            if (notifications && notifications.length > 0) {
                notifications.forEach(msg => showToast(msg, 'success'));
            }
        })
        .catch(err => console.log('Silent error notifications', err));
}

function loadPenalites() {
    if(!currentUserId) return;
    fetch(`${API_URL}/penalites?etudiantId=${currentUserId}`)
        .then(res => res.json())
        .then(list => {
            const card = document.getElementById('penalites-card');
            const ul = document.getElementById('penalites-list');
            if(!card || !ul) return;

            if(list && list.length > 0) {
                card.style.display = 'block';
                ul.innerHTML = '';
                list.forEach(msg => {
                    ul.innerHTML += `<li>${msg}</li>`;
                });
            } else {
                card.style.display = 'none';
            }
        })
        .catch(e => console.error(e));
}

// --- UI UTILS ---
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if(!container) return; // Should not happen

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    
    // Icon based on type
    let icon = 'ℹ️';
    if(type === 'success') icon = '✅';
    if(type === 'error') icon = '❌';

    toast.innerHTML = `<span>${icon}</span> <span>${message}</span>`;
    
    container.appendChild(toast);

    // Remove after 3 seconds
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(10px)';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}
