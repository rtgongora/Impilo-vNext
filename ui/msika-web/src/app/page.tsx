export default function Home() {
  return (
    <div>
      <h2 className="text-2xl font-bold mb-4">MSIKA Core Dashboard</h2>
      <p className="text-muted-foreground mb-8">
        National registry of health products, services, orderables, chargeables, and capability taxonomies.
      </p>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <a href="/catalogs" className="block p-6 bg-card rounded-lg shadow hover:shadow-md border border-border">
          <h3 className="font-semibold text-primary mb-2">Catalogs</h3>
          <p className="text-sm text-muted-foreground">Create and manage versioned catalogs with governance workflows</p>
        </a>
        <a href="/items" className="block p-6 bg-card rounded-lg shadow hover:shadow-md border border-border">
          <h3 className="font-semibold text-primary mb-2">Items</h3>
          <p className="text-sm text-muted-foreground">Products, services, orderables, chargeables, capabilities</p>
        </a>
        <a href="/import" className="block p-6 bg-card rounded-lg shadow hover:shadow-md border border-border">
          <h3 className="font-semibold text-primary mb-2">Import</h3>
          <p className="text-sm text-muted-foreground">CSV upload, external source connectors, mapping workflows</p>
        </a>
        <a href="/mappings" className="block p-6 bg-card rounded-lg shadow hover:shadow-md border border-border">
          <h3 className="font-semibold text-primary mb-2">Mapping Queue</h3>
          <p className="text-sm text-muted-foreground">Review and approve external code mappings</p>
        </a>
        <a href="/publish" className="block p-6 bg-card rounded-lg shadow hover:shadow-md border border-border">
          <h3 className="font-semibold text-primary mb-2">Publish</h3>
          <p className="text-sm text-muted-foreground">Review diffs, step-up auth, publish catalogs</p>
        </a>
        <a href="/browse" className="block p-6 bg-card rounded-lg shadow hover:shadow-md border border-border">
          <h3 className="font-semibold text-primary mb-2">Browse</h3>
          <p className="text-sm text-muted-foreground">Read-only search and discovery across published catalogs</p>
        </a>
      </div>
    </div>
  );
}
