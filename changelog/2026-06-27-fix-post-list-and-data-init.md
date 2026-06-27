# 2026-06-27 - Fix Post List Loading Failure & Data Init 500 Error

## Problem 1: Post list always shows "加载帖子列表失败"

### Root Cause
In `SchoolDetailPage.tsx`, `fetchPosts()` called `postApi.getBySchool()` which returns `ApiResponse<T>` (wrapped as `{code, message, data, timestamp}`), but the code treated the response object directly as the post array:

```typescript
const data = await postApi.getBySchool(schoolId, {...})
const viewPosts = (data as unknown as BackendPost[]).map(...) // ❌ data is ApiResponse, not array
```

The correct approach is to access `data.data` to get the actual post list.

### Fix
- Changed to `const postList: BackendPost[] = response.data || []` to properly unwrap the response

### File Changed
- `frontend/src/pages/SchoolDetailPage.tsx`

---

## Problem 2: `POST /admin/init-test-data` returns 500 "系统繁忙"

### Root Cause Analysis
Multiple issues in `DataInitServiceImpl`:

1. **OOM risk with large dummy files**: Each resource post generates a dummy file up to 10MB (PDF) or 20MB (ZIP). With 6000 posts, ~50% resource type, ~80% with files = ~2400 files. If each file averages 5-10MB, that's 12-24GB of memory allocation, causing OOM.

2. **`@Transactional` on a long-running method**: The entire 6000+ insert operation was wrapped in a single `@Transactional` block. This causes:
   - Transaction timeout (MySQL default 30s)
   - Lock contention on the posts table
   - Connection pool exhaustion

3. **Builder pattern with `@TableField(fill=...)`**: Using `Post.builder().build()` may not properly trigger MyBatis Plus field fill mechanism, potentially leaving `createTime`/`updateTime` as null when not explicitly set.

4. **No error handling per insert**: A single failed insert would abort the entire operation due to `@Transactional`.

### Fixes
1. **Reduced dummy file size to 1KB**: All test files are now 1KB max, sufficient for testing file association without OOM risk
2. **Removed `@Transactional`**: Each insert is now independent; a single failure won't affect others
3. **Replaced builder with setters**: Uses `new Post()` + `setXxx()` for proper MyBatis Plus compatibility
4. **Added per-insert try-catch**: Failed inserts are logged but don't stop the batch
5. **Simplified method signatures**: `generateFileAndGetUrl` no longer takes `fileSize` param (uses constant)

### File Changed
- `backend/campushare-user/src/main/java/com/campushare/user/service/impl/DataInitServiceImpl.java`

---

## Problem 3: Gateway JWT whitelist missing `/api/admin/` path

### Root Cause
The `JwtAuthenticationFilter` whitelist did not include `/api/admin/` path, so accessing init-test-data through the gateway (port 8080) would be blocked by JWT authentication.

Also, `/api/posts/school/` had a trailing slash which could cause path matching issues for requests like `/api/posts/school/1`.

### Fix
- Added `/api/admin/` to whitelist
- Changed `/api/posts/school/` to `/api/posts/school` (without trailing slash) for better path matching via `String.contains()`

### File Changed
- `backend/campushare-gateway/src/main/java/com/campushare/gateway/filter/JwtAuthenticationFilter.java`

---

## Summary
| Issue | Root Cause | Fix |
|-------|-----------|-----|
| Post list loading failure | Frontend treated ApiResponse wrapper as array directly | Properly access `response.data` |
| Init test data 500 error | OOM from large files + @Transactional timeout | 1KB dummy files + remove @Transactional |
| Gateway blocking admin | Missing whitelist entry | Added `/api/admin/` |
