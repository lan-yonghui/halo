<script lang="ts" setup>
import LazyImage from "@/components/image/LazyImage.vue";
import { isImage } from "@/utils/image";
import { matchMediaTypes } from "@/utils/media-type";
import type { Attachment, Group } from "@halo-dev/api-client";
import {
  IconArrowLeft,
  IconArrowRight,
  IconCheckboxCircle,
  IconCheckboxFill,
  IconEye,
  IconUpload,
  VButton,
  VCard,
  VEmpty,
  VPagination,
  VSpace,
} from "@halo-dev/components";
import type { AttachmentLike } from "@halo-dev/console-shared";
import { computed, ref, watchEffect } from "vue";
import { useAttachmentControl } from "../../composables/use-attachment";
import AttachmentDetailModal from "../AttachmentDetailModal.vue";
import AttachmentGroupList from "../AttachmentGroupList.vue";
import AttachmentUploadModal from "../AttachmentUploadModal.vue";

const props = withDefaults(
  defineProps<{
    selected: AttachmentLike[];
    accepts?: string[];
    min?: number;
    max?: number;
  }>(),
  {
    selected: () => [],
    accepts: () => ["*/*"],
    min: undefined,
    max: undefined,
  }
);

const emit = defineEmits<{
  (event: "update:selected", attachments: AttachmentLike[]): void;
  (event: "change-provider", providerId: string): void;
}>();

const selectedGroup = ref();
const page = ref(1);
const size = ref(20);

const {
  attachments,
  isLoading,
  total,
  selectedAttachment,
  selectedAttachments,
  handleFetchAttachments,
  handleSelect,
  handleSelectPrevious,
  handleSelectNext,
  handleReset,
  isChecked,
} = useAttachmentControl({
  groupName: selectedGroup,
  accepts: computed(() => {
    return props.accepts;
  }),
  page,
  size,
});

const uploadVisible = ref(false);
const detailVisible = ref(false);

watchEffect(() => {
  emit("update:selected", Array.from(selectedAttachments.value));
});

const handleOpenDetail = (attachment: Attachment) => {
  selectedAttachment.value = attachment;
  detailVisible.value = true;
};

const isDisabled = (attachment: Attachment) => {
  const isMatchMediaType = matchMediaTypes(
    attachment.spec.mediaType || "*/*",
    props.accepts
  );

  if (
    props.max !== undefined &&
    props.max <= selectedAttachments.value.size &&
    !isChecked(attachment)
  ) {
    return true;
  }

  return !isMatchMediaType;
};

function onUploadModalClose() {
  handleFetchAttachments();
  uploadVisible.value = false;
}

function onGroupSelect(group: Group) {
  selectedGroup.value = group.metadata.name;
  handleReset();
}
</script>
<template>
  <AttachmentGroupList readonly @select="onGroupSelect" />
  <div v-if="attachments?.length" class="mb-5">
    <VButton @click="uploadVisible = true">
      <template #icon>
        <IconUpload class="h-full w-full" />
      </template>
      {{ $t("core.common.buttons.upload") }}
    </VButton>
  </div>
  <VEmpty
    v-if="!attachments?.length && !isLoading"
    :message="$t('core.attachment.empty.message')"
    :title="$t('core.attachment.empty.title')"
  >
    <template #actions>
      <VSpace>
        <VButton @click="handleFetchAttachments">
          {{ $t("core.common.buttons.refresh") }}
        </VButton>
        <VButton type="secondary" @click="uploadVisible = true">
          <template #icon>
            <IconUpload class="h-full w-full" />
          </template>
          {{ $t("core.attachment.empty.actions.upload") }}
        </VButton>
      </VSpace>
    </template>
  </VEmpty>
  <div
    v-else
    class="mt-2 grid grid-cols-3 gap-x-2 gap-y-3 sm:grid-cols-3 md:grid-cols-5"
    role="list"
  >
    <VCard
      v-for="(attachment, index) in attachments"
      :key="index"
      :body-class="['!p-0']"
      :class="{
        'ring-1 ring-primary': isChecked(attachment),
        'pointer-events-none !cursor-not-allowed opacity-50':
          isDisabled(attachment),
      }"
      class="hover:shadow"
      @click.stop="handleSelect(attachment)"
    >
      <div class="group relative bg-white">
        <div
          class="aspect-h-8 aspect-w-10 block h-full w-full cursor-pointer overflow-hidden bg-gray-100"
        >
          <LazyImage
            v-if="isImage(attachment.spec.mediaType)"
            :key="attachment.metadata.name"
            :alt="attachment.spec.displayName"
            :src="attachment.status?.permalink"
            classes="pointer-events-none object-cover group-hover:opacity-75 transform-gpu"
          >
            <template #loading>
              <div class="flex h-full items-center justify-center object-cover">
                <span class="text-xs text-gray-400">
                  {{ $t("core.common.status.loading") }}...
                </span>
              </div>
            </template>
            <template #error>
              <div class="flex h-full items-center justify-center object-cover">
                <span class="text-xs text-red-400">
                  {{ $t("core.common.status.loading_error") }}
                </span>
              </div>
            </template>
          </LazyImage>
          <AttachmentFileTypeIcon
            v-else
            :file-name="attachment.spec.displayName"
          />
        </div>
        <p
          class="pointer-events-none block truncate px-2 py-1 text-center text-xs font-medium text-gray-700"
        >
          {{ attachment.spec.displayName }}
        </p>

        <div
          :class="{ '!flex': selectedAttachments.has(attachment) }"
          class="absolute left-0 top-0 hidden h-1/3 w-full justify-end bg-gradient-to-b from-gray-300 to-transparent ease-in-out group-hover:flex"
        >
          <IconEye
            class="mr-1 mt-1 hidden h-6 w-6 cursor-pointer text-white transition-all hover:text-primary group-hover:block"
            @click.stop="handleOpenDetail(attachment)"
          />
          <IconCheckboxFill
            :class="{
              '!text-primary': selectedAttachments.has(attachment),
            }"
            class="mr-1 mt-1 h-6 w-6 cursor-pointer text-white transition-all hover:text-primary"
          />
        </div>
      </div>
    </VCard>
  </div>
  <div class="mt-4">
    <VPagination
      v-model:page="page"
      v-model:size="size"
      :page-label="$t('core.components.pagination.page_label')"
      :size-label="$t('core.components.pagination.size_label')"
      :total-label="
        $t('core.components.pagination.total_label', { total: total })
      "
      :total="total"
      :size-options="[20, 50, 100]"
    />
  </div>
  <AttachmentUploadModal v-if="uploadVisible" @close="onUploadModalClose" />
  <AttachmentDetailModal
    v-model:visible="detailVisible"
    :mount-to-body="true"
    :attachment="selectedAttachment"
    @close="selectedAttachment = undefined"
  >
    <template #actions>
      <span
        v-if="selectedAttachment && selectedAttachments.has(selectedAttachment)"
        @click="handleSelect(selectedAttachment)"
      >
        <IconCheckboxFill />
      </span>
      <span v-else @click="handleSelect(selectedAttachment)">
        <IconCheckboxCircle />
      </span>

      <span @click="handleSelectPrevious">
        <IconArrowLeft />
      </span>
      <span @click="handleSelectNext">
        <IconArrowRight />
      </span>
    </template>
  </AttachmentDetailModal>
</template>
