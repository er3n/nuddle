// PhotoStorage on the private Supabase Storage bucket `task-photos` (migration
// 20261011143400_task_photos_bucket.sql). One folder per task; deleted when the task closes.
import { randomUUID } from "node:crypto";
import { createClient } from "@supabase/supabase-js";
import type { PhotoStorage } from "../../domain/ports";
import type { TaskId } from "../../domain/types";

const BUCKET = "task-photos";

export class SupabasePhotoStorage implements PhotoStorage {
  private readonly client: ReturnType<typeof createClient>;

  constructor(url: string, secretKey: string) {
    this.client = createClient(url, secretKey, {
      auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
    });
  }

  async createUploadUrl(taskId: TaskId): Promise<{ path: string; url: string }> {
    const path = `tasks/${taskId}/${randomUUID()}.jpg`;
    const { data, error } = await this.client.storage.from(BUCKET).createSignedUploadUrl(path);
    if (error) throw error;
    return { path: data.path, url: data.signedUrl };
  }

  async deleteForTask(taskId: TaskId): Promise<void> {
    const folder = `tasks/${taskId}`;
    const { data, error } = await this.client.storage.from(BUCKET).list(folder, { limit: 1000 });
    if (error) throw error;
    if (data.length === 0) return;
    const removed = await this.client.storage
      .from(BUCKET)
      .remove(data.map((f) => `${folder}/${f.name}`));
    if (removed.error) throw removed.error;
  }
}
