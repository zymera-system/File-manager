export let currentSort = 'name';
export let sortAsc = true;

export function sortFiles(by) {
    if (currentSort === by) sortAsc = !sortAsc;
    else { currentSort = by; sortAsc = true; }
}
