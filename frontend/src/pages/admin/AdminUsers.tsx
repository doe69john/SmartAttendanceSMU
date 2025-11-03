import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { ScrollArea } from '@/components/ui/scroll-area';
import { useToast } from '@/hooks/use-toast';
import { cn } from '@/lib/utils';
import {
  deleteAdminUser,
  deleteFaceData,
  fetchAdminUsers,
  updateAdminUser,
  type AdminUserSummary,
} from '@/lib/api';
import {
  CameraOff,
  Edit,
  GraduationCap,
  Loader2,
  RefreshCw,
  Search,
  Trash2,
  UserCheck,
  Users,
} from 'lucide-react';

interface EditFormState {
  id: string;
  fullName: string;
  email: string;
  phone: string;
  identifier: string;
  active: boolean;
  role: 'professor' | 'student';
}

type TabKey = 'professors' | 'students';

const ROLE_FILTER: Record<TabKey, 'professor' | 'student'> = {
  professors: 'professor',
  students: 'student',
};

const IDENTIFIER_LABEL: Record<'professor' | 'student', string> = {
  professor: 'Staff ID',
  student: 'Student ID',
};

const EMPTY_FORM: EditFormState = {
  id: '',
  fullName: '',
  email: '',
  phone: '',
  identifier: '',
  active: true,
  role: 'student',
};

const MAX_LIMIT = 200;

const normalizePhone = (value: string) => {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
};

const AdminUsers = () => {
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState<TabKey>('professors');
  const [searchTerm, setSearchTerm] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [editOpen, setEditOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<AdminUserSummary | null>(null);
  const [faceDataTarget, setFaceDataTarget] = useState<AdminUserSummary | null>(null);
  const [formState, setFormState] = useState<EditFormState>(EMPTY_FORM);

  useEffect(() => {
    const timeout = setTimeout(() => {
      setDebouncedSearch(searchTerm.trim());
    }, 300);
    return () => clearTimeout(timeout);
  }, [searchTerm]);

  const roleFilter = useMemo(() => ROLE_FILTER[activeTab], [activeTab]);

  const {
    data: users = [],
    isFetching,
    refetch,
  } = useQuery<AdminUserSummary[]>({
    queryKey: ['admin-users', { role: roleFilter, search: debouncedSearch }],
    queryFn: () =>
      fetchAdminUsers({
        role: roleFilter,
        query: debouncedSearch || undefined,
        limit: MAX_LIMIT,
      }),
    keepPreviousData: true,
  });

  const editMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: Parameters<typeof updateAdminUser>[1] }) =>
      updateAdminUser(id, payload),
    onSuccess: () => {
      toast({ title: 'User updated', description: 'Changes saved successfully.' });
      setEditOpen(false);
      setFormState(EMPTY_FORM);
      void queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : 'Failed to update user';
      toast({ title: 'Update failed', description: message, variant: 'destructive' });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteAdminUser(id),
    onSuccess: () => {
      toast({ title: 'User deleted', description: 'The account and related data were deleted.' });
      setDeleteTarget(null);
      void queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : 'Failed to delete user';
      toast({ title: 'Delete failed', description: message, variant: 'destructive' });
    },
  });

  const deleteFaceMutation = useMutation({
    mutationFn: (userId: string) => deleteFaceData({ studentId: userId }),
    onSuccess: () => {
      toast({ title: 'Face data removed', description: 'Stored biometric data was cleared for the student.' });
      setFaceDataTarget(null);
      void queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
    onError: (error: unknown) => {
      const message = error instanceof Error ? error.message : 'Failed to delete face data';
      toast({ title: 'Delete failed', description: message, variant: 'destructive' });
    },
  });

  const openEditDialog = (user: AdminUserSummary) => {
    setFormState({
      id: user.id,
      fullName: user.fullName ?? '',
      email: user.email ?? '',
      phone: user.phone ?? '',
      identifier: user.role === 'professor' ? user.staffId ?? '' : user.studentId ?? '',
      active: user.active !== false,
      role: user.role === 'professor' ? 'professor' : 'student',
    });
    setEditOpen(true);
  };

  const resetEdit = () => {
    setFormState(EMPTY_FORM);
    setEditOpen(false);
  };

  const handleSave = () => {
    if (!formState.id) {
      return;
    }
    editMutation.mutate({
      id: formState.id,
      payload: {
        fullName: formState.fullName.trim(),
        email: formState.email.trim(),
        phone: normalizePhone(formState.phone),
        staffId: formState.role === 'professor' ? formState.identifier.trim() || null : undefined,
        studentId: formState.role === 'student' ? formState.identifier.trim() || null : undefined,
        active: formState.active,
      },
    });
  };

  const currentIdentifierLabel = IDENTIFIER_LABEL[formState.role];

  const hasResults = users.length > 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">User Management</h1>
          <p className="text-muted-foreground">
            Manage professor and student accounts, update profile information, and remove users when necessary.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={() => refetch()} disabled={isFetching}>
            {isFetching ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <RefreshCw className="mr-2 h-4 w-4" />}
            Refresh
          </Button>
        </div>
      </div>

      <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as TabKey)} className="w-full">
        <Card>
          <CardHeader className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-col gap-1">
              <CardTitle className="text-xl">Directory</CardTitle>
              <CardDescription>Search and filter all users by role.</CardDescription>
            </div>
            <TabsList className="grid w-full grid-cols-2 sm:w-auto sm:flex">
              <TabsTrigger value="professors" className="flex items-center gap-2">
                <Users className="h-4 w-4" />
                Professors
              </TabsTrigger>
              <TabsTrigger value="students" className="flex items-center gap-2">
                <GraduationCap className="h-4 w-4" />
                Students
              </TabsTrigger>
            </TabsList>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="relative flex-1">
                <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder={`Search ${roleFilter === 'professor' ? 'professors' : 'students'} by name, email, or ID`}
                  value={searchTerm}
                  onChange={(event) => setSearchTerm(event.target.value)}
                  className="pl-8"
                />
              </div>
              <div className="text-sm text-muted-foreground">
                Showing {users.length} result{users.length === 1 ? '' : 's'}
              </div>
            </div>

            <TabsContent value="professors" className="mt-0">
              <UserTable
                users={roleFilter === 'professor' ? users : []}
                onEdit={openEditDialog}
                onDelete={setDeleteTarget}
                onClearFaceData={setFaceDataTarget}
                busy={isFetching}
              />
            </TabsContent>
            <TabsContent value="students" className="mt-0">
              <UserTable
                users={roleFilter === 'student' ? users : []}
                onEdit={openEditDialog}
                onDelete={setDeleteTarget}
                onClearFaceData={setFaceDataTarget}
                busy={isFetching}
              />
            </TabsContent>

            {!hasResults && !isFetching && (
              <div className="rounded-md border border-dashed p-8 text-center text-sm text-muted-foreground">
                No users found. Adjust your filters or try a different search term.
              </div>
            )}
          </CardContent>
        </Card>
      </Tabs>

      <Dialog open={editOpen} onOpenChange={(open) => (open ? setEditOpen(true) : resetEdit())}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>Edit {formState.role === 'professor' ? 'Professor' : 'Student'}</DialogTitle>
            <DialogDescription>Update contact details and identifiers for this account.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="fullName">Full Name</Label>
              <Input
                id="fullName"
                value={formState.fullName}
                onChange={(event) => setFormState((prev) => ({ ...prev, fullName: event.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                value={formState.email}
                onChange={(event) => setFormState((prev) => ({ ...prev, email: event.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="phone">Phone</Label>
              <Input
                id="phone"
                value={formState.phone}
                onChange={(event) => setFormState((prev) => ({ ...prev, phone: event.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="identifier">{currentIdentifierLabel}</Label>
              <Input
                id="identifier"
                value={formState.identifier}
                onChange={(event) => setFormState((prev) => ({ ...prev, identifier: event.target.value }))}
              />
            </div>
            <div className="flex items-center justify-between rounded-md border p-3">
              <div>
                <Label className="text-sm font-medium">Active Account</Label>
                <p className="text-xs text-muted-foreground">Inactive users cannot access the platform.</p>
              </div>
              <Switch
                checked={formState.active}
                onCheckedChange={(checked) => setFormState((prev) => ({ ...prev, active: checked }))}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={resetEdit} disabled={editMutation.isPending}>
              Cancel
            </Button>
            <Button onClick={handleSave} disabled={editMutation.isPending}>
              {editMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Save Changes
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={Boolean(deleteTarget)} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete this user?</AlertDialogTitle>
            <AlertDialogDescription>
              {deleteTarget
                ? `This will permanently remove ${deleteTarget.fullName ?? 'this user'} and all related data.`
                : null}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteMutation.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Delete User
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={Boolean(faceDataTarget)} onOpenChange={(open) => !open && setFaceDataTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Clear stored face data?</AlertDialogTitle>
            <AlertDialogDescription>
              Removing face data requires the student to re-enrol before automatic attendance can be recorded again.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleteFaceMutation.isPending}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => faceDataTarget && deleteFaceMutation.mutate(faceDataTarget.id)}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={deleteFaceMutation.isPending}
            >
              {deleteFaceMutation.isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Delete Face Data
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
};

interface UserTableProps {
  users: AdminUserSummary[];
  onEdit: (user: AdminUserSummary) => void;
  onDelete: (user: AdminUserSummary) => void;
  onClearFaceData: (user: AdminUserSummary) => void;
  busy?: boolean;
}

const UserTable = ({ users, onEdit, onDelete, onClearFaceData, busy }: UserTableProps) => {
  return (
    <div className="rounded-lg border">
      <ScrollArea className="max-h-[520px] w-full">
        <Table>
          <TableHeader className="bg-muted/60">
            <TableRow>
              <TableHead className="min-w-[220px]">Name</TableHead>
              <TableHead className="min-w-[220px]">Email</TableHead>
              <TableHead className="min-w-[140px]">Phone</TableHead>
              <TableHead className="min-w-[140px]">Identifier</TableHead>
              <TableHead className="min-w-[100px]">Status</TableHead>
              <TableHead className="w-[140px] text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {busy && users.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="py-10 text-center text-sm text-muted-foreground">
                  <Loader2 className="mr-2 inline h-4 w-4 animate-spin" /> Loading users...
                </TableCell>
              </TableRow>
            )}
            {users.map((user) => {
              const identifier = user.role === 'professor' ? user.staffId : user.studentId;
              const isStudent = user.role === 'student';
              const faceDataCount = user.faceDataCount ?? 0;
              const hasFaceData = Boolean(user.hasFaceData ?? (faceDataCount > 0));
              const faceDataLabel = hasFaceData
                ? faceDataCount > 1
                  ? `Face data enrolled (${faceDataCount})`
                  : 'Face data enrolled'
                : 'No face data';
              return (
                <TableRow key={user.id} className="hover:bg-muted/40">
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <UserCheck className="h-4 w-4 text-muted-foreground" />
                      <div className="flex flex-col">
                        <span className="font-medium text-foreground">{user.fullName}</span>
                        <span className="text-xs capitalize text-muted-foreground">{user.role}</span>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-col">
                      <span className="text-sm text-foreground">{user.email}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <span className="text-sm text-foreground">{user.phone || '—'}</span>
                  </TableCell>
                  <TableCell>
                    <span className="text-sm text-foreground">{identifier || '—'}</span>
                  </TableCell>
                  <TableCell>
                    <div className="flex flex-col gap-1">
                      <Badge
                        variant="outline"
                        className={cn(
                          'min-w-[82px] justify-center whitespace-nowrap px-3',
                          user.active === false
                            ? 'border-rose-200 bg-rose-500/10 text-rose-700'
                            : 'border-emerald-200 bg-emerald-500/10 text-emerald-700',
                        )}
                      >
                        {user.active === false ? 'Inactive' : 'Active'}
                      </Badge>
                      {isStudent && (
                        <Badge
                          variant="outline"
                          className={cn(
                            'justify-center whitespace-nowrap px-3',
                            hasFaceData
                              ? 'border-sky-200 bg-sky-500/10 text-sky-700'
                              : 'border-dashed border-muted-foreground/40 text-muted-foreground',
                          )}
                        >
                          {faceDataLabel}
                        </Badge>
                      )}
                    </div>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-2">
                      {isStudent && hasFaceData && (
                        <Button
                          variant="outline"
                          size="icon"
                          className="h-8 w-8"
                          onClick={() => onClearFaceData(user)}
                          title="Delete face data"
                        >
                          <CameraOff className="h-4 w-4" />
                        </Button>
                      )}
                      <Button variant="outline" size="icon" className="h-8 w-8" onClick={() => onEdit(user)} title="Edit">
                        <Edit className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="outline"
                        size="icon"
                        className="h-8 w-8 text-destructive hover:bg-destructive/10"
                        onClick={() => onDelete(user)}
                        title="Delete"
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              );
            })}
            {!busy && users.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} className="py-10 text-center text-sm text-muted-foreground">
                  No users to display for this filter.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </ScrollArea>
    </div>
  );
};

export default AdminUsers;
